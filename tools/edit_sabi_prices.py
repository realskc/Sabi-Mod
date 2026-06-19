#!/usr/bin/env python3
import copy
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import tkinter as tk
from dataclasses import dataclass
from pathlib import Path
from tkinter import messagebox, ttk


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[1]
CONFIG_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "sabi" / "sabi_machine"
ITEMS_PATH = CONFIG_DIR / "items.json"
BASE_PRICES_PATH = CONFIG_DIR / "base_prices.json"
DERIVED_PRICES_PATH = CONFIG_DIR / "derived_prices.json"
VANILLA_ASSETS_ROOT = (
    REPO_ROOT
    / "build"
    / "neoForm"
    / "neoFormJoined26.1.2-1"
    / "steps"
    / "transformSource"
    / "transformed"
    / "assets"
)
MOD_ASSETS_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets"

BASE_COMMENT = (
    "Manual Sabi machine base prices. Prices are in Xiao Sabi. Items listed here are not "
    "calculated from recipes, or were intentionally kept as base prices."
)


def load_json(path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def split_id(identifier, default_namespace="minecraft"):
    if ":" in identifier:
        namespace, path = identifier.split(":", 1)
    else:
        namespace, path = default_namespace, identifier
    return namespace, path


def redeem_price(pawn_price):
    pawn_price = max(0, int(pawn_price))
    return (pawn_price * 6 + 4) // 5


def display_number(value):
    if value is None:
        return "-"
    return f"{int(value):,}"


def get_price(object_):
    return max(0, int(object_.get("pawn_price", 0)))


def set_price(object_, value):
    object_["pawn_price"] = max(0, int(value))


@dataclass(frozen=True)
class ManualEntry:
    kind: str
    index: int
    object_: dict

    @property
    def id(self):
        return str(self.object_.get("id", ""))

    @property
    def items(self):
        return list(self.object_.get("items", []))

    @property
    def comment(self):
        return str(self.object_.get("comment", ""))

    @property
    def price(self):
        return get_price(self.object_)

    @property
    def label(self):
        prefix = "符号" if self.kind == "symbol" else "基础组"
        return f"{prefix}: {self.id}"


class JavaPriceResolverClient:
    def __init__(self):
        self.gradlew = REPO_ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")

    def resolve(self, items_config, base_config, derived_config):
        with tempfile.TemporaryDirectory(prefix="sabi-price-") as temp_dir:
            temp = Path(temp_dir)
            items_path = temp / "items.json"
            base_path = temp / "base_prices.json"
            derived_path = temp / "derived_prices.json"
            output_path = temp / "resolved_prices.json"
            write_json(items_path, items_config)
            write_json(base_path, base_config)
            write_json(derived_path, derived_config)

            env = os.environ.copy()
            env["SABI_PRICE_ITEMS"] = str(items_path)
            env["SABI_PRICE_BASE"] = str(base_path)
            env["SABI_PRICE_DERIVED"] = str(derived_path)
            env["SABI_PRICE_OUTPUT"] = str(output_path)
            completed = subprocess.run(
                [str(self.gradlew), "-q", "resolveSabiPrices", "--no-daemon"],
                cwd=REPO_ROOT,
                env=env,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=180,
            )
            if completed.returncode != 0:
                details = "\n".join(part for part in (completed.stdout, completed.stderr) if part.strip())
                raise RuntimeError(details.strip() or "Java 价格解析任务失败。")
            if not output_path.exists():
                details = "\n".join(part for part in (completed.stdout, completed.stderr) if part.strip())
                raise RuntimeError(details.strip() or "Java 价格解析任务没有生成输出文件。")
            return load_json(output_path)


class AssetResolver:
    def __init__(self):
        self.direct_roots = [MOD_ASSETS_ROOT, VANILLA_ASSETS_ROOT]
        self.object_root = None
        self.object_index = {}
        self._model_cache = {}
        self._load_asset_index()

    def _load_asset_index(self):
        minecraft_dirs = []
        gradle_properties = REPO_ROOT / "gradle.properties"
        if gradle_properties.exists():
            text = gradle_properties.read_text(encoding="utf-8", errors="ignore")
            match = re.search(r"^pcl_mods_dir\s*=\s*(.+)$", text, re.MULTILINE)
            if match:
                mods_dir = Path(match.group(1).strip())
                parts = list(mods_dir.parts)
                if ".minecraft" in parts:
                    minecraft_dirs.append(Path(*parts[: parts.index(".minecraft") + 1]))

        appdata = os.environ.get("APPDATA")
        if appdata:
            minecraft_dirs.append(Path(appdata) / ".minecraft")

        for minecraft_dir in minecraft_dirs:
            indexes_dir = minecraft_dir / "assets" / "indexes"
            objects_dir = minecraft_dir / "assets" / "objects"
            if not indexes_dir.exists() or not objects_dir.exists():
                continue
            for index_path in sorted(indexes_dir.glob("*.json"), reverse=True):
                try:
                    index = load_json(index_path).get("objects", {})
                except Exception:
                    continue
                if "minecraft/lang/zh_cn.json" in index or "minecraft/lang/en_us.json" in index:
                    self.object_root = objects_dir
                    self.object_index = index
                    return

    def path_for(self, logical_path):
        logical_path = logical_path.replace("\\", "/").removeprefix("assets/")
        for root in self.direct_roots:
            path = root / Path(logical_path)
            if path.exists():
                return path

        entry = self.object_index.get(logical_path)
        if entry and self.object_root:
            digest = entry.get("hash", "")
            path = self.object_root / digest[:2] / digest
            if path.exists():
                return path
        return None

    def load_json_asset(self, logical_path):
        path = self.path_for(logical_path)
        if not path:
            return None
        try:
            return load_json(path)
        except Exception:
            return None

    def load_lang(self, namespace, language):
        return self.load_json_asset(f"{namespace}/lang/{language}.json") or {}

    def texture_for_item(self, item_id):
        namespace, path = split_id(item_id)
        texture = self._texture_from_model(namespace, f"item/{path}", set())
        if texture:
            return texture
        return self._first_existing(
            namespace,
            [
                f"item/{path}.png",
                f"block/{path}.png",
                f"block/{path}_front.png",
                f"block/{path}_side.png",
                f"block/{path}_top.png",
            ],
        )

    def _texture_from_model(self, namespace, model_name, seen):
        key = f"{namespace}:{model_name}"
        if key in self._model_cache:
            return self._model_cache[key]
        if key in seen:
            return None
        seen.add(key)

        model = self.load_json_asset(f"{namespace}/models/{model_name}.json")
        if not model:
            self._model_cache[key] = None
            return None

        textures = model.get("textures", {})
        texture_keys = ["layer0", "all", "front", "side", "top", "bottom"]
        if model_name.startswith("block/"):
            texture_keys.append("particle")

        for texture_key in texture_keys:
            texture_id = textures.get(texture_key)
            texture_path = self._texture_path(texture_id, namespace, textures)
            if texture_path:
                self._model_cache[key] = texture_path
                return texture_path

        skipped_keys = {"particle"} if not model_name.startswith("block/") else set()
        for texture_key, texture_id in textures.items():
            if texture_key in skipped_keys:
                continue
            texture_path = self._texture_path(texture_id, namespace, textures)
            if texture_path:
                self._model_cache[key] = texture_path
                return texture_path

        parent = model.get("parent")
        if parent:
            parent_namespace, parent_model = split_id(parent, namespace)
            texture_path = self._texture_from_model(parent_namespace, parent_model, seen)
            self._model_cache[key] = texture_path
            return texture_path

        self._model_cache[key] = None
        return None

    def _texture_path(self, texture_id, namespace, textures):
        if not texture_id:
            return None
        while texture_id.startswith("#"):
            texture_id = textures.get(texture_id[1:], "")
            if not texture_id:
                return None
        texture_namespace, texture_path = split_id(texture_id, namespace)
        return self.path_for(f"{texture_namespace}/textures/{texture_path}.png")

    def _first_existing(self, namespace, texture_paths):
        for texture_path in texture_paths:
            path = self.path_for(f"{namespace}/textures/{texture_path}")
            if path:
                return path
        return None


class NameResolver:
    def __init__(self, assets):
        self.lang = {}
        self.lang.update(assets.load_lang("minecraft", "en_us"))
        self.lang.update(assets.load_lang("minecraft", "zh_cn"))
        self.lang.update(assets.load_lang("sabi", "en_us"))
        self.lang.update(assets.load_lang("sabi", "zh_cn"))

    def item_name(self, item_id):
        namespace, path = split_id(item_id)
        keys = [f"item.{namespace}.{path}", f"block.{namespace}.{path}"]
        for key in keys:
            value = self.lang.get(key)
            if value:
                return value
        return path.replace("_", " ")


class PriceWorkspace:
    def __init__(self, items_config, base_config, derived_config, resolver_client):
        self.items_config = items_config
        self.base_config = base_config
        self.derived_config = derived_config
        self.resolver_client = resolver_client
        self.allowed_items = self._read_allowed_items()
        self.allowed_item_set = set(self.allowed_items)
        self.allowed_group_by_item = self._read_allowed_groups()
        self.manual_entries = self._read_manual_entries()
        self.resolution = {}
        self.resolved_by_item = {}
        self.direct_dependents = {}
        self.rebuild_resolution()

    def rebuild_resolution(self):
        self.apply_resolution(self.resolve_configs(*self.snapshot_configs()))

    def snapshot_configs(self):
        return (
            copy.deepcopy(self.items_config),
            copy.deepcopy(self.base_config),
            copy.deepcopy(self.derived_config),
        )

    def resolve_configs(self, items_config, base_config, derived_config):
        return self.resolver_client.resolve(
            items_config,
            base_config,
            derived_config,
        )

    def apply_resolution(self, resolution):
        self.resolution = resolution
        self.resolved_by_item = {
            item.get("id", ""): item
            for item in self.resolution.get("items", [])
        }
        self.direct_dependents = self.resolution.get("direct_dependents", {})

    def price(self, item_id):
        return self.resolved_by_item.get(item_id, {}).get("pawn_price")

    def source_label(self, item_id):
        return self.resolved_by_item.get(item_id, {}).get("source", "未定价")

    def formulas(self, item_id):
        return self.resolved_by_item.get(item_id, {}).get("formulas", [])

    def save_base_prices(self):
        if "comment" not in self.base_config:
            self.base_config["comment"] = BASE_COMMENT
        write_json(BASE_PRICES_PATH, self.base_config)

    def _read_allowed_items(self):
        result = []
        seen = set()
        for group in self.items_config.get("groups", []):
            for item_id in group.get("items", []):
                if item_id not in seen:
                    seen.add(item_id)
                    result.append(item_id)
        return result

    def _read_allowed_groups(self):
        result = {}
        for group in self.items_config.get("groups", []):
            group_id = group.get("id", "")
            for item_id in group.get("items", []):
                result.setdefault(item_id, group_id)
        return result

    def _read_manual_entries(self):
        entries = []
        for index, symbol in enumerate(self.base_config.get("symbols", [])):
            entries.append(ManualEntry("symbol", index, symbol))
        for index, group in enumerate(self.base_config.get("groups", [])):
            entries.append(ManualEntry("group", index, group))
        return entries


class PriceEditor(tk.Tk):
    def __init__(self, workspace, assets, names):
        super().__init__()
        self.title("撒币机价格编辑器")
        self.geometry("1040x720")
        self.minsize(900, 620)

        self.workspace = workspace
        self.assets = assets
        self.names = names
        self.index = 0
        self.current_image = None
        self.item_tree_ids = {}
        self.refresh_status_var = tk.StringVar(value="价格预览已就绪")
        self.refresh_running = False
        self.refresh_queued = False

        self._build_widgets()
        self._bind_shortcuts()
        self._refresh_all_items()
        self._show_entry(0)

    def _build_widgets(self):
        root = ttk.Frame(self, padding=10)
        root.pack(fill=tk.BOTH, expand=True)

        header = ttk.Frame(root)
        header.pack(fill=tk.X, pady=(0, 8))
        ttk.Label(header, text="手工编辑 base_prices.json；派生价只做预览。").pack(side=tk.LEFT)
        ttk.Label(header, textvariable=self.refresh_status_var).pack(side=tk.LEFT, padx=(24, 0))
        ttk.Button(header, text="保存 base_prices.json", command=self.save_file).pack(side=tk.RIGHT)

        self.notebook = ttk.Notebook(root)
        self.notebook.pack(fill=tk.BOTH, expand=True)
        self._build_manual_tab()
        self._build_preview_tab()

    def _build_manual_tab(self):
        tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(tab, text="基础价编辑")

        top = ttk.Frame(tab)
        top.pack(fill=tk.X)
        self.progress_label = ttk.Label(top, text="")
        self.progress_label.pack(side=tk.LEFT)
        ttk.Label(top, text="搜索基础组/物品：").pack(side=tk.LEFT, padx=(24, 4))
        self.search_var = tk.StringVar()
        ttk.Entry(top, textvariable=self.search_var, width=34).pack(side=tk.LEFT)
        ttk.Button(top, text="查找", command=self.find_next).pack(side=tk.LEFT, padx=4)

        body = ttk.Frame(tab)
        body.pack(fill=tk.BOTH, expand=True, pady=(10, 8))

        preview = ttk.LabelFrame(body, text="物品信息", padding=10)
        preview.pack(side=tk.LEFT, fill=tk.Y)
        self.image_label = ttk.Label(preview, text="无贴图", anchor=tk.CENTER)
        self.image_label.configure(width=20)
        self.image_label.pack(pady=(4, 8))
        self.texture_label = ttk.Label(preview, text="", wraplength=180)
        self.texture_label.pack(fill=tk.X)
        self.item_info_label = ttk.Label(preview, text="", wraplength=180, justify=tk.LEFT)
        self.item_info_label.pack(fill=tk.X, pady=(10, 0))

        details = ttk.Frame(body)
        details.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(12, 0))
        self.entry_label = ttk.Label(details, text="", font=("", 14, "bold"))
        self.entry_label.pack(anchor=tk.W)
        self.entry_meta_label = ttk.Label(details, text="", wraplength=650, justify=tk.LEFT)
        self.entry_meta_label.pack(anchor=tk.W, pady=(4, 8))

        list_frame = ttk.LabelFrame(details, text="组内物品", padding=6)
        list_frame.pack(fill=tk.BOTH, expand=True)
        self.item_list = tk.Listbox(list_frame, height=12)
        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL, command=self.item_list.yview)
        self.item_list.configure(yscrollcommand=scrollbar.set)
        self.item_list.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.item_list.bind("<<ListboxSelect>>", self.on_manual_item_selected)

        dependents_frame = ttk.LabelFrame(details, text="直接使用这些基础价的派生物品", padding=6)
        dependents_frame.pack(fill=tk.BOTH, expand=True, pady=(8, 0))
        self.dependents_text = tk.Text(dependents_frame, height=6, wrap=tk.WORD)
        self.dependents_text.configure(state=tk.DISABLED)
        self.dependents_text.pack(fill=tk.BOTH, expand=True)

        price_frame = ttk.Frame(tab)
        price_frame.pack(fill=tk.X, pady=(4, 8))
        ttk.Label(price_frame, text="典当价：").pack(side=tk.LEFT)
        self.price_var = tk.StringVar()
        self.price_entry = ttk.Entry(price_frame, textvariable=self.price_var, width=14)
        self.price_entry.pack(side=tk.LEFT)
        self.price_var.trace_add("write", lambda *_: self.update_redeem_preview())
        self.redeem_label = ttk.Label(price_frame, text="")
        self.redeem_label.pack(side=tk.LEFT, padx=(12, 0))

        buttons = ttk.Frame(tab)
        buttons.pack(fill=tk.X)
        ttk.Button(buttons, text="上一项", command=self.previous_entry).pack(side=tk.LEFT)
        ttk.Button(buttons, text="保存并下一项", command=self.save_and_next).pack(side=tk.LEFT, padx=6)
        ttk.Button(buttons, text="跳过下一项", command=self.next_entry).pack(side=tk.LEFT)
        ttk.Button(buttons, text="仅应用当前值", command=self.save_current).pack(side=tk.LEFT, padx=6)
        ttk.Button(buttons, text="保存文件", command=self.save_file).pack(side=tk.RIGHT)

    def _build_preview_tab(self):
        tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(tab, text="最终价格预览")

        top = ttk.Frame(tab)
        top.pack(fill=tk.X, pady=(0, 8))
        ttk.Label(top, text="搜索白名单物品：").pack(side=tk.LEFT)
        self.preview_search_var = tk.StringVar()
        self.preview_search_var.trace_add("write", lambda *_: self._refresh_all_items())
        ttk.Entry(top, textvariable=self.preview_search_var, width=38).pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="重新计算", command=self.schedule_resolution_refresh).pack(side=tk.LEFT, padx=4)
        self.preview_summary_label = ttk.Label(top, text="")
        self.preview_summary_label.pack(side=tk.RIGHT)

        paned = ttk.PanedWindow(tab, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)

        list_frame = ttk.Frame(paned)
        paned.add(list_frame, weight=3)
        columns = ("name", "pawn", "redeem", "source", "group")
        self.item_tree = ttk.Treeview(list_frame, columns=columns, show="headings", selectmode="browse")
        self.item_tree.heading("name", text="物品")
        self.item_tree.heading("pawn", text="典当价")
        self.item_tree.heading("redeem", text="赎回价")
        self.item_tree.heading("source", text="来源")
        self.item_tree.heading("group", text="白名单组")
        self.item_tree.column("name", width=260)
        self.item_tree.column("pawn", width=80, anchor=tk.E)
        self.item_tree.column("redeem", width=80, anchor=tk.E)
        self.item_tree.column("source", width=150)
        self.item_tree.column("group", width=140)
        yscroll = ttk.Scrollbar(list_frame, orient=tk.VERTICAL, command=self.item_tree.yview)
        self.item_tree.configure(yscrollcommand=yscroll.set)
        self.item_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        yscroll.pack(side=tk.RIGHT, fill=tk.Y)
        self.item_tree.bind("<<TreeviewSelect>>", self.on_preview_item_selected)

        right = ttk.Frame(paned)
        paned.add(right, weight=2)
        ttk.Label(right, text="派生公式 / 来源", font=("", 11, "bold")).pack(anchor=tk.W)
        self.formula_text = tk.Text(right, wrap=tk.WORD, height=18)
        self.formula_text.configure(state=tk.DISABLED)
        self.formula_text.pack(fill=tk.BOTH, expand=True, pady=(6, 0))

    def _bind_shortcuts(self):
        self.bind("<Control-s>", lambda _event: self.save_file())
        self.bind("<Return>", lambda _event: self.save_and_next())
        self.bind("<Alt-Left>", lambda _event: self.previous_entry())
        self.bind("<Alt-Right>", lambda _event: self.next_entry())

    def _show_entry(self, index):
        entries = self.workspace.manual_entries
        if not entries:
            messagebox.showerror("错误", f"{BASE_PRICES_PATH.name} 中没有 symbols 或 groups。")
            return

        self.index = max(0, min(index, len(entries) - 1))
        entry = entries[self.index]
        items = entry.items

        self.progress_label.configure(text=f"{self.index + 1} / {len(entries)}")
        self.entry_label.configure(text=entry.label)
        self.price_var.set(str(entry.price))
        self.entry_meta_label.configure(text=self._entry_meta(entry))

        self.item_list.delete(0, tk.END)
        if entry.kind == "symbol":
            self.item_list.insert(tk.END, f"{entry.id}  ({entry.comment or '虚拟价格符号'})")
        else:
            for item_id in items:
                allowed = "白名单" if item_id in self.workspace.allowed_item_set else "非白名单"
                self.item_list.insert(tk.END, f"{self.names.item_name(item_id)}  ({item_id}, {allowed})")

        if items:
            self.item_list.selection_set(0)
            self.show_item_texture(items[0])
        else:
            self.show_virtual_symbol(entry)

        self._show_direct_dependents(entry)
        self.price_entry.focus_set()
        self.price_entry.selection_range(0, tk.END)

    def _entry_meta(self, entry):
        if entry.kind == "symbol":
            return f"虚拟符号价。{entry.comment}" if entry.comment else "虚拟符号价。"

        total = len(entry.items)
        allowlisted = sum(1 for item in entry.items if item in self.workspace.allowed_item_set)
        return (
            f"组内物品：{total}；其中白名单物品：{allowlisted}。"
            "这里的价格会作为组内每个基础物品的典当价，并参与派生价递归计算。"
        )

    def _show_direct_dependents(self, entry):
        dependencies = []
        source_items = [entry.id] if entry.kind == "symbol" else entry.items
        for item_id in source_items:
            for dependent in self.workspace.direct_dependents.get(item_id, []):
                dependencies.append(dependent)
        dependencies = sorted(dict.fromkeys(dependencies))

        lines = []
        if dependencies:
            for item_id in dependencies[:80]:
                price = self.workspace.price(item_id)
                lines.append(
                    f"{self.names.item_name(item_id)} ({item_id})  典当价 {display_number(price)}"
                )
            if len(dependencies) > 80:
                lines.append(f"... 另有 {len(dependencies) - 80} 项")
        else:
            lines.append("没有派生公式直接引用这一项。")

        self.dependents_text.configure(state=tk.NORMAL)
        self.dependents_text.delete("1.0", tk.END)
        self.dependents_text.insert(tk.END, "\n".join(lines))
        self.dependents_text.configure(state=tk.DISABLED)

    def show_virtual_symbol(self, entry):
        self.image_label.configure(image="", text="虚拟符号")
        self.texture_label.configure(text=entry.id)
        self.item_info_label.configure(
            text=f"{entry.id}\n典当价 {display_number(entry.price)}\n赎回价 {display_number(redeem_price(entry.price))}"
        )
        self.current_image = None

    def show_item_texture(self, item_id):
        price = self.workspace.price(item_id)
        source = self.workspace.source_label(item_id)
        info = (
            f"{self.names.item_name(item_id)}\n{item_id}\n"
            f"{source}\n典当价 {display_number(price)}\n"
            f"赎回价 {display_number(redeem_price(price) if price is not None else None)}"
        )
        self.item_info_label.configure(text=info)

        texture = self.assets.texture_for_item(item_id)
        if not texture:
            self.image_label.configure(image="", text="未找到贴图")
            self.texture_label.configure(text=item_id)
            self.current_image = None
            return

        try:
            image = tk.PhotoImage(file=str(texture))
            scale = max(1, min(8, 128 // max(1, max(image.width(), image.height()))))
            if scale > 1:
                image = image.zoom(scale, scale)
            self.current_image = image
            self.image_label.configure(image=image, text="")
            self.texture_label.configure(text=Path(texture).name)
        except Exception:
            self.image_label.configure(image="", text="贴图无法显示")
            self.texture_label.configure(text=str(texture))
            self.current_image = None

    def on_manual_item_selected(self, _event=None):
        entry = self.workspace.manual_entries[self.index]
        selection = self.item_list.curselection()
        if entry.kind == "symbol":
            self.show_virtual_symbol(entry)
            return
        if selection:
            items = entry.items
            item_index = selection[0]
            if item_index < len(items):
                self.show_item_texture(items[item_index])

    def on_preview_item_selected(self, _event=None):
        selection = self.item_tree.selection()
        if not selection:
            return
        item_id = self.item_tree_ids.get(selection[0])
        if not item_id:
            return
        self._show_formula_text(item_id)

    def _show_formula_text(self, item_id):
        price = self.workspace.price(item_id)
        source = self.workspace.source_label(item_id)
        lines = [
            f"{self.names.item_name(item_id)} ({item_id})",
            f"来源：{source}",
            f"典当价：{display_number(price)}",
            f"赎回价：{display_number(redeem_price(price) if price is not None else None)}",
            "",
        ]

        formulas = self.workspace.formulas(item_id)
        if not formulas:
            lines.append("没有派生公式。")
        else:
            lines.append("派生公式：")
            for formula in formulas:
                lines.append(
                    f"- {formula.get('recipe_id') or '(unnamed)'} [{formula.get('type', '')}] "
                    f"=> {display_number(formula.get('price'))} / result_count={float(formula.get('result_count', 1.0)):g}"
                )
                for term in formula.get("ingredients", []):
                    count = float(term.get("count", 1.0))
                    rendered = []
                    for candidate in term.get("candidates", []):
                        candidate_id = candidate.get("id", "")
                        rendered.append(
                            f"{self.names.item_name(candidate_id)} ({candidate_id}, {display_number(candidate.get('price'))})"
                        )
                    lines.append(f"  * {count:g} x " + " 或 ".join(rendered))

        self.formula_text.configure(state=tk.NORMAL)
        self.formula_text.delete("1.0", tk.END)
        self.formula_text.insert(tk.END, "\n".join(lines))
        self.formula_text.configure(state=tk.DISABLED)

    def parse_price(self):
        value = self.price_var.get().strip()
        if not re.fullmatch(r"\d+", value):
            raise ValueError("典当价必须是非负整数。")
        return int(value)

    def update_redeem_preview(self):
        try:
            pawn = self.parse_price()
            self.redeem_label.configure(text=f"赎回价自动计算：{display_number(redeem_price(pawn))}")
        except ValueError:
            self.redeem_label.configure(text="赎回价自动计算：-")

    def schedule_resolution_refresh(self):
        if self.refresh_running:
            self.refresh_queued = True
            self.refresh_status_var.set("价格预览刷新排队中...")
            return

        snapshot = self.workspace.snapshot_configs()
        self.refresh_running = True
        self.refresh_status_var.set("正在后台刷新价格预览...")
        thread = threading.Thread(target=self._resolve_prices_in_background, args=(snapshot,), daemon=True)
        thread.start()

    def _resolve_prices_in_background(self, snapshot):
        try:
            resolution = self.workspace.resolve_configs(*snapshot)
            self.after(0, lambda resolution=resolution: self._finish_resolution_refresh(resolution, None))
        except Exception as exc:
            self.after(0, lambda exc=exc: self._finish_resolution_refresh(None, exc))

    def _finish_resolution_refresh(self, resolution, error):
        self.refresh_running = False
        if self.refresh_queued:
            self.refresh_queued = False
            self.schedule_resolution_refresh()
            return

        if error is not None:
            self.refresh_status_var.set("价格预览刷新失败")
            messagebox.showerror("Java 价格解析失败", str(error))
            return

        self.workspace.apply_resolution(resolution)
        self.refresh_status_var.set("价格预览已更新")
        self._show_direct_dependents(self.workspace.manual_entries[self.index])
        self._refresh_all_items()
        self.on_manual_item_selected()

    def save_current(self):
        try:
            price = self.parse_price()
        except ValueError as exc:
            messagebox.showerror("价格无效", str(exc))
            return False

        entry = self.workspace.manual_entries[self.index]
        old_price = entry.price
        set_price(entry.object_, price)
        if price != old_price:
            self.refresh_status_var.set("基础价已修改，价格预览待刷新")
            self.schedule_resolution_refresh()
        return True

    def save_and_next(self):
        if self.save_current():
            self.workspace.save_base_prices()
            self.next_entry()

    def save_file(self):
        if self.save_current():
            self.workspace.save_base_prices()
            messagebox.showinfo("已保存", f"已写入：{BASE_PRICES_PATH}")

    def previous_entry(self):
        if self.save_current():
            self._show_entry(self.index - 1)

    def next_entry(self):
        if self.index >= len(self.workspace.manual_entries) - 1:
            messagebox.showinfo("完成", "已经到最后一项。")
            return
        self._show_entry(self.index + 1)

    def find_next(self):
        query = self.search_var.get().strip().lower()
        if not query:
            return

        entries = self.workspace.manual_entries
        for offset in range(1, len(entries) + 1):
            index = (self.index + offset) % len(entries)
            entry = entries[index]
            haystack = [entry.id, entry.comment]
            for item_id in entry.items:
                haystack.append(item_id)
                haystack.append(self.names.item_name(item_id))
            if query in " ".join(haystack).lower():
                if self.save_current():
                    self._show_entry(index)
                return
        messagebox.showinfo("未找到", f"没有找到：{query}")

    def _refresh_all_items(self):
        if not hasattr(self, "item_tree"):
            return
        query = self.preview_search_var.get().strip().lower() if hasattr(self, "preview_search_var") else ""
        self.item_tree.delete(*self.item_tree.get_children())
        self.item_tree_ids.clear()

        shown = 0
        unresolved = 0
        for order, item_id in enumerate(self.workspace.allowed_items):
            name = self.names.item_name(item_id)
            source = self.workspace.source_label(item_id)
            group = self.workspace.allowed_group_by_item.get(item_id, "")
            price = self.workspace.price(item_id)
            if price is None:
                unresolved += 1
            haystack = f"{name} {item_id} {source} {group}".lower()
            if query and query not in haystack:
                continue

            iid = f"item-{order}"
            self.item_tree_ids[iid] = item_id
            self.item_tree.insert(
                "",
                tk.END,
                iid=iid,
                values=(
                    f"{name} ({item_id})",
                    display_number(price),
                    display_number(redeem_price(price) if price is not None else None),
                    source,
                    group,
                ),
            )
            shown += 1

        self.preview_summary_label.configure(
            text=f"显示 {shown} / {len(self.workspace.allowed_items)}；未定价 {unresolved}"
        )


def main():
    missing = [path for path in (ITEMS_PATH, BASE_PRICES_PATH, DERIVED_PRICES_PATH) if not path.exists()]
    if missing:
        for path in missing:
            print(f"找不到配置文件：{path}", file=sys.stderr)
        return 1

    items_config = load_json(ITEMS_PATH)
    base_config = load_json(BASE_PRICES_PATH)
    derived_config = load_json(DERIVED_PRICES_PATH)
    assets = AssetResolver()
    names = NameResolver(assets)
    try:
        workspace = PriceWorkspace(items_config, base_config, derived_config, JavaPriceResolverClient())
    except Exception as exc:
        print(f"Java 价格解析失败：{exc}", file=sys.stderr)
        return 1
    app = PriceEditor(workspace, assets, names)
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
