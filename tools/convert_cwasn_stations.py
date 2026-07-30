# -*- coding: utf-8 -*-
"""
将 CWASN 测站列表 StationXML 转换为 Playground 模式所需的格式。

输入文件格式（FDSN StationXML）：
    <FDSNStationXML>
      <Network code="TW">
        <Station code="ALS">
          <Latitude>23.50838</Latitude>
          <Longitude>120.81341</Longitude>
          <Elevation>2417.0</Elevation>
          <Site><Name>Alishan Weather Station</Name></Site>
          <Channel code="EHZ" locationCode="10">
            <Response>
              <InstrumentSensitivity>
                <Value>112381514.59</Value>
              </InstrumentSensitivity>
            </Response>
          </Channel>
        </Station>
      </Network>
    </FDSNStationXML>

输出文件格式（Playground）：
    network, station, name, lat, lon, alt, sensitivity, site, vs30

说明：
    - 提取所有 Network 下的所有 Station
    - 灵敏度从第一个含 InstrumentSensitivity 的 Channel 提取，若无则用默认值
    - network 字段使用 Network code（如 TW），便于按网络筛选

用法：
    python convert_cwasn_stations.py
"""

import csv
import json
import os
import sys
import xml.etree.ElementTree as ET

# ===== 路径配置 =====
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# 输入文件
INPUT_XML = os.path.join(PROJECT_ROOT, ".files", "CWASN.stationXML.xml")

# 输出文件夹
OUTPUT_FOLDER_NAME = "cwasn_stations"
OUTPUT_FOLDER = os.path.join(PROJECT_ROOT, "playground_stations", OUTPUT_FOLDER_NAME)
OUTPUT_CSV = os.path.join(OUTPUT_FOLDER, "stations.csv")
OUTPUT_JSON = os.path.join(OUTPUT_FOLDER, "info.json")

# 默认灵敏度
DEFAULT_SENSITIVITY = 7e10

# FDSN StationXML 命名空间
NS = "{http://www.fdsn.org/xml/station/1}"


def find_text(element, tag):
    """在元素下查找指定标签的文本"""
    child = element.find(f"{NS}{tag}")
    if child is None:
        # 尝试不带命名空间
        child = element.find(tag)
    if child is not None and child.text is not None:
        return child.text.strip()
    return None


def find_attr(element, tag, attr):
    """查找指定标签的属性"""
    child = element.find(f"{NS}{tag}")
    if child is None:
        child = element.find(tag)
    if child is not None:
        return child.get(attr)
    return None


def main():
    # 检查输入文件
    if not os.path.exists(INPUT_XML):
        print(f"错误：找不到输入文件 {INPUT_XML}")
        sys.exit(1)

    # 创建输出文件夹
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)

    # 解析 XML
    try:
        tree = ET.parse(INPUT_XML)
    except ET.ParseError as e:
        print(f"错误：XML 解析失败: {e}")
        sys.exit(1)

    root = tree.getroot()

    stations = []
    net_stats = {}

    # 遍历所有 Network
    for network_elem in root.iter(f"{NS}Network"):
        network_code = network_elem.get("code", "UNKNOWN")

        # 遍历该 Network 下的所有 Station
        for station_elem in network_elem.findall(f"{NS}Station"):
            station_code = station_elem.get("code", "")
            if not station_code:
                continue

            lat_str = find_text(station_elem, "Latitude")
            lon_str = find_text(station_elem, "Longitude")
            elev_str = find_text(station_elem, "Elevation")

            if lat_str is None or lon_str is None:
                continue

            try:
                lat = float(lat_str)
                lon = float(lon_str)
                alt = float(elev_str) if elev_str else 0.0
            except ValueError:
                continue

            # 台站名称
            site_elem = station_elem.find(f"{NS}Site")
            if site_elem is None:
                site_elem = station_elem.find("Site")
            name = ""
            if site_elem is not None:
                name_text = find_text(site_elem, "Name")
                if name_text:
                    name = name_text

            if not name:
                name = station_code

            # 尝试从 Channel 提取灵敏度
            sensitivity = DEFAULT_SENSITIVITY
            for channel_elem in station_elem.findall(f"{NS}Channel"):
                # 查找 InstrumentSensitivity/Value
                sens_value = None
                for sens_elem in channel_elem.iter(f"{NS}InstrumentSensitivity"):
                    val = find_text(sens_elem, "Value")
                    if val:
                        try:
                            sens_value = float(val)
                        except ValueError:
                            pass
                    if sens_value is not None:
                        break

                # 不带命名空间的 fallback
                if sens_value is None:
                    for sens_elem in channel_elem.iter("InstrumentSensitivity"):
                        val = find_text(sens_elem, "Value")
                        if val:
                            try:
                                sens_value = float(val)
                            except ValueError:
                                pass
                        if sens_value is not None:
                            break

                if sens_value is not None and sens_value > 0:
                    sensitivity = sens_value
                    break

            stations.append({
                "network": network_code,
                "station": station_code,
                "name": name,
                "lat": lat,
                "lon": lon,
                "alt": alt,
                "sensitivity": sensitivity,
                "site": "CWASN",
                "vs30": "",
            })

            net_stats[network_code] = net_stats.get(network_code, 0) + 1

    print(f"共提取 {len(stations)} 个台站")

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
        "name": "CWASN测站列表",
        "author": "中央气象署 (Central Weather Administration, Taiwan)",
        "description": (
            "本测站列表来自台湾中央气象署地震测报中心（CWASN），"
            "为 FDSN StationXML 格式转换而来。"
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
