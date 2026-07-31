# -*- coding: utf-8 -*-
"""分析 intensity_debug.log（含阶段标记版）：
按 (站, 震级, 距离段, 阶段) 分组，取组内 pga 最大记录的烈度，
分别统计 P 后 S 前 / S 后 两阶段每站最大烈度分布。
"""
import re
from collections import defaultdict, Counter

LOG = r"f:\trae_projects\GlobalQuakeBetterPlaygroundPlus\intensity_debug.log"

pat = re.compile(
    r"\[DEBUG\] (\S+) \| ratio=([\d.]+) vel=([\d.]+) sens=([\d.E+-]+) pga=([\d.]+) \| (.+) \| 阶段=(\S+) \| 烈度=(\S+)"
)

LVL_ORDER = {"1": 1, "2": 2, "3": 3, "4": 4, "5-": 5, "5+": 6, "6-": 7, "6+": 8, "7": 9}


def lvl_key(s):
    return LVL_ORDER.get(s, 0)


bins = [(0, 50), (50, 100), (100, 200), (200, 400), (400, 800), (800, 2000)]


def bin_of(d):
    for lo, hi in bins:
        if lo <= d < hi:
            return f"{lo}-{hi}km"
    return "2000+km"


# group[(station, mag, bin, phase)] -> (max_pga, level)
group = {}
n_total = 0
n_linked = 0
with open(LOG, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        line = line.strip()
        if not line:
            continue
        m = pat.match(line)
        if not m:
            continue
        n_total += 1
        code, ratio, vel, sens, pga, rest, phase, lvl = m.groups()
        pga = float(pga)
        if rest in ("无活动地震", "无地震分析"):
            continue
        qm = re.search(r"M([\d.]+) 距([\d.]+)km", rest)
        if not qm:
            continue
        n_linked += 1
        mag = round(float(qm.group(1)), 1)
        dist = float(qm.group(2))
        key = (code, mag, bin_of(dist), phase)
        if key not in group or pga > group[key][0]:
            group[key] = (pga, lvl)

# 按震级聚合：phase -> bin -> Counter(level)
by_mag = defaultdict(lambda: defaultdict(lambda: defaultdict(Counter)))
station_cnt = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
for (code, mag, b, phase), (pga, lvl) in group.items():
    by_mag[mag][phase][b][lvl] += 1
    station_cnt[mag][phase][b] += 1

print(f"总记录数: {n_total}  关联地震记录: {n_linked}  分组(站×震级×距离段×阶段): {len(group)}")
mags = sorted(by_mag)
print("涉及的测定震级:", [f"M{m}" for m in mags])

for mag in mags:
    print(f"\n===== M{mag} =====")
    for phase in ("P", "S"):
        if phase not in by_mag[mag]:
            continue
        print(f"  --- 阶段={phase} ({'P波后S波前' if phase == 'P' else 'S波后'}) ---")
        for lo, hi in bins:
            b = f"{lo}-{hi}km"
            cnt = by_mag[mag][phase].get(b, Counter())
            if not cnt:
                continue
            dist_str = " ".join(f"{k}×{v}" for k, v in sorted(cnt.items(), key=lambda x: lvl_key(x[0])))
            print(f"    {lo:>4}-{hi:>4}km | 站数={station_cnt[mag][phase][b]:>3} | {dist_str}")
