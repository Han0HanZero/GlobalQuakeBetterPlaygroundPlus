# -*- coding: utf-8 -*-
"""
将 TREM-net 测站列表 JSON 转换为 Playground 模式所需的格式。

输入文件格式（TREM-net 原始 JSON）：
    {
      "<id>": {
        "net": "SE-Net",
        "info": [
          {"code": 242, "lat": 25.02, "lon": 121.42, "time": "2024-07-06"},
          ...
        ],
        "work": true/false
      },
      ...
    }

输出文件格式（Playground）：
    network, station, name, lat, lon, alt, sensitivity, site, vs30

说明：
    - 只提取 work == true 的台站（正在工作的）
    - 一个台站可能有多个 info 记录（历史位置），取最新的（按 time 排序）
    - network 字段使用 net 值（如 SE-Net、MS-Net），便于按网络筛选

用法：
    python convert_tremnet_stations.py
"""

import csv
import json
import os
import sys
from datetime import datetime

# ===== 路径配置 =====
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# 输入文件
INPUT_JSON = os.path.join(PROJECT_ROOT, ".files", "tremnetstation.json")

# 输出文件夹
OUTPUT_FOLDER_NAME = "trem-net_stations"
OUTPUT_FOLDER = os.path.join(PROJECT_ROOT, "playground_stations", OUTPUT_FOLDER_NAME)
OUTPUT_CSV = os.path.join(OUTPUT_FOLDER, "stations.csv")
OUTPUT_JSON = os.path.join(OUTPUT_FOLDER, "info.json")

# 默认灵敏度
DEFAULT_SENSITIVITY = 7e10


def parse_time(time_str):
    """解析时间字符串，返回可用于排序的 datetime 对象"""
    try:
        return datetime.strptime(time_str, "%Y-%m-%d")
    except (ValueError, TypeError):
        return datetime.min


def main():
    # 检查输入文件
    if not os.path.exists(INPUT_JSON):
        print(f"错误：找不到输入文件 {INPUT_JSON}")
        sys.exit(1)

    # 创建输出文件夹
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)

    # 读取 JSON
    with open(INPUT_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    print(f"共读取 {len(data)} 条台站记录")

    stations = []
    net_stats = {}  # 统计各网络的台站数
    skipped_not_work = 0
    skipped_no_info = 0

    for station_id, info in data.items():
        # 只取 work == true 的台站
        if not info.get("work", False):
            skipped_not_work += 1
            continue

        info_list = info.get("info", [])
        if not info_list:
            skipped_no_info += 1
            continue

        # 按 time 排序，取最新的记录
        sorted_info = sorted(info_list, key=lambda x: parse_time(x.get("time", "")))
        latest = sorted_info[-1]

        lat = latest.get("lat")
        lon = latest.get("lon")
        if lat is None or lon is None:
            skipped_no_info += 1
            continue

        net = info.get("net", "TREM-net")
        code = latest.get("code", "")

        stations.append({
            "network": net,
            "station": str(station_id),
            "name": f"{net}-{code}",
            "lat": float(lat),
            "lon": float(lon),
            "alt": 0.0,
            "sensitivity": DEFAULT_SENSITIVITY,
            "site": "TREM-net",
            "vs30": "",
        })

        net_stats[net] = net_stats.get(net, 0) + 1

    print(f"跳过 {skipped_not_work} 个未工作的台站")
    print(f"跳过 {skipped_no_info} 个无信息的台站")
    print(f"共提取 {len(stations)} 个工作台站")

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
        "name": "TREM-net测站列表",
        "author": "TREM-net",
        "description": (
            "本测站列表来自 TREM-net（台湾地区地震监测网），包含 SE-Net 和 MS-Net "
            "等子网络。仅包含当前正在工作的台站，每个台站取最新位置记录。"
            f"共包含 {len(stations)} 个台站。"
        ),
    }
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)

    print(f"已写入 info.json: {OUTPUT_JSON}")

    # 打印各网络统计
    print("\n各网络台站数：")
    for net in sorted(net_stats.keys()):
        print(f"  {net}: {net_stats[net]}")


if __name__ == "__main__":
    main()
