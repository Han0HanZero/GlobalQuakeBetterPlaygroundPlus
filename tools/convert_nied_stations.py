# -*- coding: utf-8 -*-
"""
将日本 NIED 测站列表 CSV 转换为 Playground 模式所需的格式。

输入文件格式（NIED 原始）：
    organization_id, network_id, station_cd, station_name, pref,
    latitude, longitude, height(m), sensor_height(m), hole_depth(m),
    KiK-net_station_cd, repeal_station(1 = repeal)
    （值用单引号包裹）

输出文件格式（Playground）：
    network, station, name, lat, lon, alt, sensitivity, site, vs30

说明：
    - 只提取 network_id == 1 的台站（Hi-net）
    - 排除已废止的台站（repeal_station == 1）
    - network 字段使用都道府县（pref），便于在导入对话框按地区筛选
    - 同时生成 info.json 元数据文件

用法：
    python convert_nied_stations.py
"""

import csv
import json
import os
import sys

# ===== 路径配置 =====
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# 输入文件（NIED 原始 CSV）
INPUT_CSV = os.path.join(PROJECT_ROOT, ".files", "NIED_SeismicStation_20260730.csv")

# 输出文件夹
OUTPUT_FOLDER_NAME = "hi-net_stations"
OUTPUT_FOLDER = os.path.join(PROJECT_ROOT, "playground_stations", OUTPUT_FOLDER_NAME)
OUTPUT_CSV = os.path.join(OUTPUT_FOLDER, "stations.csv")
OUTPUT_JSON = os.path.join(OUTPUT_FOLDER, "info.json")

# 默认灵敏度（与 PlaygroundStation.DEFAULT_SENSITIVITY 一致）
DEFAULT_SENSITIVITY = 7e10


def clean_value(val):
    """去除值两端的单引号和多余空白"""
    if val is None:
        return ""
    val = val.strip()
    # NIED 的 CSV 用单引号包裹值，如 '01'
    if len(val) >= 2 and val.startswith("'") and val.endswith("'"):
        val = val[1:-1]
    return val.strip()


def main():
    # 检查输入文件
    if not os.path.exists(INPUT_CSV):
        print(f"错误：找不到输入文件 {INPUT_CSV}")
        sys.exit(1)

    # 创建输出文件夹
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)

    stations = []
    pref_stats = {}  # 统计各都道府县的台站数

    # 读取 NIED CSV
    # 尝试不同编码，NIED 文件可能含日文
    encodings_to_try = ["utf-8", "shift_jis", "cp932", "utf-8-sig"]
    rows = None
    used_encoding = None
    for enc in encodings_to_try:
        try:
            with open(INPUT_CSV, "r", encoding=enc) as f:
                # 先读出全部行，避免中途编码错误
                content = f.read()
            # 用 csv 解析
            rows = list(csv.reader(content.splitlines()))
            used_encoding = enc
            break
        except (UnicodeDecodeError, Exception):
            continue

    if rows is None:
        print("错误：无法解码输入文件，尝试了编码: " + ", ".join(encodings_to_try))
        sys.exit(1)

    print(f"使用编码读取: {used_encoding}")
    print(f"总行数（含表头）: {len(rows)}")

    if len(rows) < 2:
        print("错误：文件没有数据行")
        sys.exit(1)

    # 解析表头
    header = [clean_value(h) for h in rows[0]]
    print(f"表头: {header}")

    # 建立列名到索引的映射
    col_map = {}
    for idx, name in enumerate(header):
        col_map[name] = idx

    # 检查必需的列
    required_cols = ["network_id", "station_cd", "station_name", "pref",
                     "latitude", "longitude", "height(m)", "repeal_station(1 = repeal)"]
    for col in required_cols:
        if col not in col_map:
            print(f"错误：缺少必需的列 '{col}'")
            sys.exit(1)

    # 逐行处理
    skipped = 0
    for row in rows[1:]:
        if not row or len(row) < len(header):
            skipped += 1
            continue

        network_id = clean_value(row[col_map["network_id"]])
        # 只取 network_id == 1（Hi-net），NIED 数据中可能是 "1" 或 "01"
        try:
            nid = int(network_id)
        except ValueError:
            continue
        if nid != 1:
            continue

        # 排除已废止台站
        repeal = clean_value(row[col_map["repeal_station(1 = repeal)"]])
        if repeal == "1":
            continue

        station_cd = clean_value(row[col_map["station_cd"]])
        station_name = clean_value(row[col_map["station_name"]])
        pref = clean_value(row[col_map["pref"]])
        lat = clean_value(row[col_map["latitude"]])
        lon = clean_value(row[col_map["longitude"]])
        height = clean_value(row[col_map["height(m)"]])

        # 跳过无效数据
        if not station_cd or not lat or not lon:
            skipped += 1
            continue

        try:
            lat_f = float(lat)
            lon_f = float(lon)
            alt_f = float(height) if height else 0.0
        except ValueError:
            skipped += 1
            continue

        # network 用都道府县，便于按地区筛选
        network = pref if pref else "Hi-net"

        stations.append({
            "network": network,
            "station": station_cd,
            "name": station_name if station_name else station_cd,
            "lat": lat_f,
            "lon": lon_f,
            "alt": alt_f,
            "sensitivity": DEFAULT_SENSITIVITY,
            "site": "Hi-net",
            "vs30": "",
        })

        # 统计
        pref_stats[network] = pref_stats.get(network, 0) + 1

    print(f"已跳过 {skipped} 行无效数据")
    print(f"共提取 {len(stations)} 个 Hi-net 台站")

    if not stations:
        print("错误：没有提取到任何台站")
        sys.exit(1)

    # 写入输出 CSV
    with open(OUTPUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["network", "station", "name", "lat", "lon",
                         "alt", "sensitivity", "site", "vs30"])
        for s in stations:
            writer.writerow([
                s["network"], s["station"], s["name"],
                f"{s['lat']:.6f}", f"{s['lon']:.6f}", f"{s['alt']:.1f}",
                f"{s['sensitivity']:.1f}", s["site"], s["vs30"],
            ])

    print(f"已写入 CSV: {OUTPUT_CSV}")

    # 写入 info.json
    info = {
        "name": "NIED Hi-net测站列表",
        "author": "NIED (日本防灾科学技术研究所)",
        "description": (
            "本测站列表来自日本防灾科学技术研究所（NIED）的 Hi-net 高灵敏度"
            "地震观测网，覆盖日本全境各都道府县。共包含 "
            f"{len(stations)} 个台站。"
        ),
    }
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)

    print(f"已写入 info.json: {OUTPUT_JSON}")

    # 打印各都道府县统计
    print("\n各都道府县台站数：")
    for pref in sorted(pref_stats.keys()):
        print(f"  {pref}: {pref_stats[pref]}")


if __name__ == "__main__":
    main()
