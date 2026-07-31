# -*- coding: utf-8 -*-
"""计算 IntensityTable.getIntensity 的衰减行为，为修复波形生成器距离衰减提供依据"""
import math

def getIntensity(mag, dist):
    limit = 1200.0
    if mag > 7.5:
        limit += (mag - 7.5) * 500
    if mag > 9:
        mag *= 1 + 0.2 * (mag - 9) ** 2.5
    mag = 1.25 * mag - 0.9 - 0.0004 * mag * mag * mag
    if dist > limit:
        dist = limit + (dist - limit) ** 0.4 * 22
    return (15 ** (mag * 0.92 + 4.0)) / (5 * (dist + 1000 / (mag + 3.0) ** 3) ** (2.0 + 0.110 * mag) + 2000 + 5 * 6.0 ** mag) / 0.07

def gcdToGeo(gcd):
    return gcd / 111.0  # 约1度≈111km

print("=== IntensityTable.getIntensity (dist 为角度 geo) ===")
for mag in [4.0, 8.1]:
    print(f"\n--- M{mag} ---")
    for km in [0, 5, 20, 50, 100, 200, 400, 800, 1200, 2000]:
        geo = gcdToGeo(km)
        v = getIntensity(mag, geo)
        dm = v / 20.0
        print(f"  {km:>5}km (geo={geo:8.3f})  intensity={v:14.3e}  distMultiplier={dm:14.3e}  相对震中={dm/getIntensity(mag, gcdToGeo(0.5))*100:8.1f}%")

print("\n=== 真实物理衰减参考（几何扩散+衰减，无单位）===")
# 参考: 震中处1，按 1/(dist^k) 理想衰减
for mag in [4.0, 8.1]:
    print(f"\n--- M{mag} 参考 ---")
    for km in [5, 20, 50, 100, 200, 400, 800]:
        # 简单几何扩散
        print(f"  {km:>5}km  1/d^1.2={100*(5.0/km)**1.2:6.1f}%  1/d^1.5={100*(5.0/km)**1.5:6.1f}%  1/d^2={100*(5.0/km)**2:6.1f}%")
