# -*- coding: utf-8 -*-
import re

line = "[DEBUG] B0101 | ratio=114.0 vel=7324.9497 sens=7.00E+10 pga=0.00 | 无活动地震 | 烈度=N/A"
pat = re.compile(
    r"\[DEBUG\] (\S+) \| ratio=([\d.]+) vel=([\d.]+) sens=([\d.E+-]+) pga=([\d.]+) \| 烈度=(\S+) \| (.+)"
)
m = pat.match(line)
print("match:", m)
if m:
    print(m.groups())
