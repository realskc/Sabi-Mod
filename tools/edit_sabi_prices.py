#!/usr/bin/env python3
import json
import os
import re
import sys
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[1]
CONFIG_PATH = REPO_ROOT / "src" / "main" / "resources" / "data" / "sabi" / "sabi_machine" / "items.json"
VANILLA_ASSETS_ROOT = REPO_ROOT / "build" / "neoForm" / "neoFormJoined26.1.2-1" / "steps" / "transformSource" / "transformed" / "assets"
MOD_ASSETS_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets"


def load_json(path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_config(path, data):
    lines = [
        "{",
        '  "comment": "Sabi machine grouped item price list. Prices are in Xiao Sabi. Items share a group only when they have the same material role and the same item form; color, pattern, species, and oxidation/wax state variants may share one price.",',
        '  "groups": [',
    ]
    groups = data.get("groups", [])
    for group_index, group in enumerate(groups):
        group_comma = "," if group_index < len(groups) - 1 else ""
        lines.append(
            '    {{ "id": "{id}", "pawn_price": {pawn}, "items": ['.format(
                id=json_escape(group.get("id", "")),
                pawn=max(0, int(group.get("pawn_price", 0))),
            )
        )
        items = group.get("items", [])
        for item_index, item in enumerate(items):
            item_comma = "," if item_index < len(items) - 1 else ""
            lines.append('      "{item}"{comma}'.format(item=json_escape(item), comma=item_comma))
        lines.append("    ] }" + group_comma)
    lines.extend(["  ]", "}"])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def json_escape(value):
    return json.dumps(str(value), ensure_ascii=False)[1:-1]


def redeem_price(pawn_price):
    pawn_price = max(0, int(pawn_price))
    return (pawn_price * 6 + 4) // 5


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
        for texture_key in ("layer0", "all", "front", "side", "top", "bottom", "particle"):
            texture_id = textures.get(texture_key)
            texture_path = self._texture_path(texture_id, namespace, textures)
            if texture_path:
                self._model_cache[key] = texture_path
                return texture_path

        for texture_id in textures.values():
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


def split_id(identifier, default_namespace="minecraft"):
    if ":" in identifier:
        namespace, path = identifier.split(":", 1)
    else:
        namespace, path = default_namespace, identifier
    return namespace, path


class PriceEditor(tk.Tk):
    def __init__(self, config, assets, names):
        super().__init__()
        self.title("撒币机典当价编辑器")
        self.geometry("760x560")
        self.minsize(680, 480)

        self.config_data = config
        self.assets = assets
        self.names = names
        self.groups = config.get("groups", [])
        self.index = 0
        self.current_image = None

        self._build_widgets()
        self._bind_shortcuts()
        self._show_group(0)

    def _build_widgets(self):
        root = ttk.Frame(self, padding=12)
        root.pack(fill=tk.BOTH, expand=True)

        top = ttk.Frame(root)
        top.pack(fill=tk.X)
        self.progress_label = ttk.Label(top, text="")
        self.progress_label.pack(side=tk.LEFT)
        ttk.Label(top, text="搜索组/物品：").pack(side=tk.LEFT, padx=(24, 4))
        self.search_var = tk.StringVar()
        search_entry = ttk.Entry(top, textvariable=self.search_var, width=28)
        search_entry.pack(side=tk.LEFT)
        ttk.Button(top, text="查找", command=self.find_next).pack(side=tk.LEFT, padx=4)

        main = ttk.Frame(root)
        main.pack(fill=tk.BOTH, expand=True, pady=(12, 8))

        preview = ttk.LabelFrame(main, text="示意贴图", padding=10)
        preview.pack(side=tk.LEFT, fill=tk.Y)
        self.image_label = ttk.Label(preview, text="无贴图", anchor=tk.CENTER)
        self.image_label.configure(width=18)
        self.image_label.pack(pady=(4, 8))
        self.texture_label = ttk.Label(preview, text="", wraplength=150)
        self.texture_label.pack(fill=tk.X)

        details = ttk.Frame(main)
        details.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(12, 0))
        self.group_label = ttk.Label(details, text="", font=("", 14, "bold"))
        self.group_label.pack(anchor=tk.W)
        self.price_hint_label = ttk.Label(details, text="")
        self.price_hint_label.pack(anchor=tk.W, pady=(4, 8))

        list_frame = ttk.LabelFrame(details, text="这一组包含的物品", padding=6)
        list_frame.pack(fill=tk.BOTH, expand=True)
        self.item_list = tk.Listbox(list_frame, height=12)
        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL, command=self.item_list.yview)
        self.item_list.configure(yscrollcommand=scrollbar.set)
        self.item_list.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.item_list.bind("<<ListboxSelect>>", self.on_item_selected)

        price_frame = ttk.Frame(root)
        price_frame.pack(fill=tk.X, pady=(6, 8))
        ttk.Label(price_frame, text="典当价：").pack(side=tk.LEFT)
        self.price_var = tk.StringVar()
        self.price_entry = ttk.Entry(price_frame, textvariable=self.price_var, width=12)
        self.price_entry.pack(side=tk.LEFT)
        self.price_var.trace_add("write", lambda *_: self.update_redeem_preview())
        self.redeem_label = ttk.Label(price_frame, text="")
        self.redeem_label.pack(side=tk.LEFT, padx=(12, 0))

        buttons = ttk.Frame(root)
        buttons.pack(fill=tk.X)
        ttk.Button(buttons, text="上一项", command=self.previous_group).pack(side=tk.LEFT)
        ttk.Button(buttons, text="保存并下一项", command=self.save_and_next).pack(side=tk.LEFT, padx=6)
        ttk.Button(buttons, text="跳过下一项", command=self.next_group).pack(side=tk.LEFT)
        ttk.Button(buttons, text="仅保存", command=self.save_current).pack(side=tk.LEFT, padx=6)
        ttk.Button(buttons, text="保存文件", command=self.save_file).pack(side=tk.RIGHT)

    def _bind_shortcuts(self):
        self.bind("<Return>", lambda _event: self.save_and_next())
        self.bind("<Control-s>", lambda _event: self.save_file())
        self.bind("<Alt-Left>", lambda _event: self.previous_group())
        self.bind("<Alt-Right>", lambda _event: self.next_group())

    def _show_group(self, index):
        if not self.groups:
            messagebox.showerror("错误", "items.json 中没有 groups。")
            return
        self.index = max(0, min(index, len(self.groups) - 1))
        group = self.groups[self.index]
        items = group.get("items", [])

        self.progress_label.configure(text=f"{self.index + 1} / {len(self.groups)}")
        self.group_label.configure(text=f"组：{group.get('id', '')}")
        self.price_var.set(str(group.get("pawn_price", 0)))
        self.price_hint_label.configure(text=f"组内物品数：{len(items)}")

        self.item_list.delete(0, tk.END)
        for item_id in items:
            self.item_list.insert(tk.END, f"{self.names.item_name(item_id)}  ({item_id})")

        if items:
            self.item_list.selection_set(0)
            self.show_item_texture(items[0])
        else:
            self.show_item_texture(None)
        self.price_entry.focus_set()
        self.price_entry.selection_range(0, tk.END)

    def show_item_texture(self, item_id):
        if not item_id:
            self.image_label.configure(image="", text="无贴图")
            self.texture_label.configure(text="")
            self.current_image = None
            return

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

    def on_item_selected(self, _event=None):
        group = self.groups[self.index]
        selection = self.item_list.curselection()
        if selection:
            items = group.get("items", [])
            item_index = selection[0]
            if item_index < len(items):
                self.show_item_texture(items[item_index])

    def parse_price(self):
        value = self.price_var.get().strip()
        if not re.fullmatch(r"\d+", value):
            raise ValueError("典当价必须是非负整数。")
        return int(value)

    def update_redeem_preview(self):
        try:
            pawn = self.parse_price()
            self.redeem_label.configure(text=f"赎回价自动计算：{redeem_price(pawn)}")
        except ValueError:
            self.redeem_label.configure(text="赎回价自动计算：-")

    def save_current(self):
        try:
            price = self.parse_price()
        except ValueError as exc:
            messagebox.showerror("价格无效", str(exc))
            return False
        self.groups[self.index]["pawn_price"] = price
        return True

    def save_and_next(self):
        if self.save_current():
            self.save_file(show_message=False)
            self.next_group()

    def save_file(self, show_message=True):
        write_config(CONFIG_PATH, self.config_data)
        if show_message:
            messagebox.showinfo("已保存", f"已写入：{CONFIG_PATH}")

    def previous_group(self):
        if self.save_current():
            self._show_group(self.index - 1)

    def next_group(self):
        if self.index >= len(self.groups) - 1:
            messagebox.showinfo("完成", "已经到最后一组。")
            return
        self._show_group(self.index + 1)

    def find_next(self):
        query = self.search_var.get().strip().lower()
        if not query:
            return
        for offset in range(1, len(self.groups) + 1):
            index = (self.index + offset) % len(self.groups)
            group = self.groups[index]
            haystack = [str(group.get("id", ""))]
            for item_id in group.get("items", []):
                haystack.append(item_id)
                haystack.append(self.names.item_name(item_id))
            if query in " ".join(haystack).lower():
                if self.save_current():
                    self._show_group(index)
                return
        messagebox.showinfo("未找到", f"没有找到：{query}")


def main():
    if not CONFIG_PATH.exists():
        print(f"找不到配置文件：{CONFIG_PATH}", file=sys.stderr)
        return 1
    config = load_json(CONFIG_PATH)
    assets = AssetResolver()
    names = NameResolver(assets)
    app = PriceEditor(config, assets, names)
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
