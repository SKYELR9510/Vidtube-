import re, sys
s = open(sys.argv[1], encoding='utf-8').read(); t = sys.argv[2]; last = len(sys.argv) > 3 and sys.argv[3] == 'last'
hits = []
for m in re.finditer(r'<node [^>]*>', s):
    n = m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        hits.append(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
if hits: print(hits[-1] if last else hits[0])
