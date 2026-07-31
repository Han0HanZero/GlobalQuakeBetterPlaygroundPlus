# -*- coding: utf-8 -*-
"""分析 intensity_debug.log：按地震震级分组，统计烈度随距离的分布，并查看同一站的时间序列"""
import re
from collections import defaultdict

LOG = r"f:\trae_projects\GlobalQuakeBetterPlaygroundPlus\intensity_debug.log"

pat = re.compile(
    r"\[DEBUG\] (\S+) \| ratio=([\d.]+) vel=([\d.]+) sens=([\d.E+-]+) pga=([\d.]+) \| 烈度=(\S+) \| (.+)"
)

# JMA 烈度排序
LVL_ORDER = {"1": 1, "2": 2, "3": 3, "4": 4, "5-": 5, "5+": 6, "6-": 7, "6+": 8, "7": 9}


def lvl_key(s):
    return LVL_ORDER.get(s, 0)


records = []  # (line, code, ratio, vel, pga, lvl, quakes)
with open(LOG, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        line = line.strip()
        if not line:
            continue
        m = pat.match(line)
        if not m:
            continue
        code, ratio, vel, sens, pga, lvl, rest = m.groups()
        quakes = []
        if rest not in ("无活动地震", "无地震分析"):
            for q in re.finditer(r"M([\d.]+) 距([\d.]+)km", rest):
                quakes.append((round(float(q.group(1)), 1), float(q.group(2))))
        records.append((i, code, float(ratio), float(vel), float(pga), lvl, quakes))

print("总记录数:", len(records))
codes = {r[1] for r in records}
print("唯一站数:", len(codes))

# ---- 1. 按震级分组 ----
print("\n===== 1. 各震级出现的记录数 =====")
mag_counts = defaultdict(int)
for r in records:
    for (mag, dist) in r[6]:
        mag_counts[mag] += 1
for mag in sorted(mag_counts):
    print(f"M{mag}: {mag_counts[mag]} 条")

# ---- 2. 按 (震级, 距离段) 统计烈度 ----
print("\n===== 2. 按震级+距离段的烈度分布 =====")
# 距离段：0-50, 50-100, 100-200, 200-400, 400-800, 800+
bins = [(0, 50), (50, 100), (100, 200), (200, 400), (400, 800), (800, 2000)]


def bin_of(d):
    for lo, hi in bins:
        if lo <= d < hi:
            return f"{lo}-{hi}km"
    return "2000+km"


by_mag_dist = defaultdict(list)  # (mag, bin) -> [lvl]
for r in records:
    for (mag, dist) in r[6]:
        by_mag_dist[(mag, bin_of(dist))].append(r[5])

for mag in sorted(mag_counts):
    print(f"\n--- M{mag} ---")
    for lo, hi in bins:
        key = (mag, f"{lo}-{hi}km")
        lvls = by_mag_dist.get(key, [])
        if not lvls:
            continue
        from collections import Counter
        cnt = Counter(lvls)
        max_lvl = max(cnt, key=lambda x: lvl_key(x)) if lvls else "-"
        # 按烈度排序输出分布
        dist_str = " ".join(f"{k}×{v}" for k, v in sorted(cnt.items(), key=lambda x: lvl_key(x[0])))
        print(f"  {lo:>4}-{hi:>4}km | n={len(lvls):>3} | 最高={max_lvl:>3} | {dist_str}")

# ---- 3. 震中附近站的烈度变化（距离<20km 的记录）----
print("\n===== 3. 震中(<20km)烈度随时间变化 =====")
epi = [r for r in records if any(d < 20 for (m, d) in r[6])]
for r in epi[:40]:
    print(f"L{r[0]:>5} {r[1]:>8} 距{[f'{d:.0f}' for (m,d) in r[6]]} 烈度={r[5]:>3} pga={r[4]:.1f}")

# ---- 4. 同一个站的多条记录示例（选几个站）----
print("\n===== 4. 同一站时间序列（示例站） =====")
sample_codes = sorted(codes)[:8]
for c in sample_codes:
    rs = [r for r in records if r[1] == c]
    print(f"\n{c} ({len(rs)} 条):")
    for r in rs[:20]:
        quake_str = ";".join(f"M{m}@{d:.0f}km" for (m, d) in r[6]) or "无地震"
        print(f"  L{r[0]:>5} 烈度={r[5]:>3} pga={r[4]:>8.1f} ratio={r[2]:>10.0f} vel={r[3]:>12.0f} {quake_str}")
