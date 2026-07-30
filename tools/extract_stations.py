"""
从强震动参数数据集中提取去重后的台站列表。

用法: python extract_stations.py <数据目录> [-o 输出文件]

数据目录: 包含 CSV 文件的文件夹（如 .files/）
输出文件: 生成的台站列表 CSV，默认为 <数据目录>/stations.csv
"""

import csv
import sys
import os
from collections import OrderedDict


def extract_stations(data_dir, output_file=None):
    if output_file is None:
        output_file = os.path.join(data_dir, "stations.csv")

    csv_files = sorted([
        f for f in os.listdir(data_dir)
        if f.endswith(".csv") and f.startswith("强震动参数数据集")
    ])

    if not csv_files:
        print(f"在 {data_dir} 中未找到 CSV 文件")
        sys.exit(1)

    print(f"找到 {len(csv_files)} 个 CSV 文件，开始处理...")

    # key: (network, station_code) -> station info
    stations = OrderedDict()

    total_rows = 0
    for csv_file in csv_files:
        filepath = os.path.join(data_dir, csv_file)
        with open(filepath, "r", encoding="utf-8-sig") as f:
            reader = csv.DictReader(f)
            for row in reader:
                total_rows += 1
                network = row.get("台网代码", "").strip()
                code = row.get("台站编码", "").strip()
                name = row.get("台站名称", "").strip() or code
                lat = row.get("台站纬度", "").strip()
                lon = row.get("台站经度", "").strip()
                site = row.get("场地标签", "").strip()
                vs30 = row.get("参考Vs30", "").strip()

                key = (network, code)
                if key not in stations:
                    stations[key] = {
                        "network": network,
                        "station": code,
                        "name": name,
                        "lat": lat,
                        "lon": lon,
                        "site": site,
                        "vs30": vs30,
                    }

        print(f"  已处理: {csv_file}")

    print(f"\n共扫描 {total_rows} 条记录，提取到 {len(stations)} 个唯一台站")

    # Write output
    with open(output_file, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "network", "station", "name", "lat", "lon",
            "alt", "sensitivity", "site", "vs30"
        ])
        for info in stations.values():
            writer.writerow([
                info["network"],
                info["station"],
                info["name"],
                info["lat"],
                info["lon"],
                0,
                7e10,
                info["site"],
                info["vs30"],
            ])

    print(f"\n台站列表已保存到: {output_file}")
    print(f"共 {len(stations)} 个台站")

    # Show some stats
    networks = {}
    for info in stations.values():
        net = info["network"]
        networks[net] = networks.get(net, 0) + 1

    print("\n各台网统计:")
    for net, count in sorted(networks.items(), key=lambda x: -x[1]):
        print(f"  {net}: {count} 个台站")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    data_dir = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None
    extract_stations(data_dir, output_file)