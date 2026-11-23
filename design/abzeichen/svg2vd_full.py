"""SVG -> Android VectorDrawable für Abzeichen und Avatare (Claude-Design-Export, Nov. 2026).

Kann mehr als design/logo/svg2vd.py: Gruppen mit geerbten Attributen (fill/stroke/opacity …), circle, ellipse,
rect (mit rx), path, rotate()-Transformationen, Kreis-Clip-Pfade, lineare Verläufe (auch objectBoundingBox)
und currentColor (wird weiß → im Code per Icon-Tint eingefärbt). Weichzeichner-Filter gibt es in
VectorDrawables nicht: solche Elemente werden als halbtransparenter, etwas breiterer Strich nachgebildet.

Aufruf: python svg2vd_full.py <quelle.svg> <ziel.xml> [breite_dp]
"""
import math
import re
import sys
import xml.etree.ElementTree as ET

NS = '{http://www.w3.org/2000/svg}'
INHERIT = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-opacity', 'stroke-opacity')


def fmt(v):
    s = ('%.3f' % float(v)).rstrip('0').rstrip('.')
    return s if s not in ('-0', '') else '0'


def color(c, opacity=1.0):
    c = c.strip()
    if c == 'currentColor':
        c = '#FFFFFF'
    if len(c) == 4 and c.startswith('#'):
        c = '#' + ''.join(ch * 2 for ch in c[1:])
    if not c.startswith('#') or len(c) != 7:
        raise ValueError('Farbe nicht unterstützt: ' + c)
    a = round(max(0.0, min(1.0, opacity)) * 255)
    return '#%02X%s' % (a, c[1:].upper())


def ellipse_path(cx, cy, rx, ry):
    return 'M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 %s,0Z' % (
        fmt(cx - rx), fmt(cy), fmt(rx), fmt(ry), fmt(2 * rx), fmt(rx), fmt(ry), fmt(-2 * rx))


def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return 'M%s,%sh%sv%sh%sZ' % (fmt(x), fmt(y), fmt(w), fmt(h), fmt(-w))
    rx = min(rx, w / 2, h / 2)
    return ('M%s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s h%s a%s,%s 0 0,1 %s,%s v%s a%s,%s 0 0,1 %s,%s Z' % (
        fmt(x + rx), fmt(y), fmt(w - 2 * rx), fmt(rx), fmt(rx), fmt(rx), fmt(rx), fmt(h - 2 * rx),
        fmt(rx), fmt(rx), fmt(-rx), fmt(rx), fmt(-(w - 2 * rx)), fmt(rx), fmt(rx), fmt(-rx), fmt(-rx),
        fmt(-(h - 2 * rx)), fmt(rx), fmt(rx), fmt(rx), fmt(-rx)))


def shape(el):
    """Pfad + Begrenzungsrahmen (für Verläufe mit objectBoundingBox)."""
    tag = el.tag.replace(NS, '')
    f = lambda k, d='0': float(el.get(k, d))
    if tag == 'circle':
        cx, cy, r = f('cx'), f('cy'), f('r')
        return ellipse_path(cx, cy, r, r), (cx - r, cy - r, 2 * r, 2 * r)
    if tag == 'ellipse':
        cx, cy, rx, ry = f('cx'), f('cy'), f('rx'), f('ry')
        return ellipse_path(cx, cy, rx, ry), (cx - rx, cy - ry, 2 * rx, 2 * ry)
    if tag == 'rect':
        x, y, w, h = f('x'), f('y'), f('width'), f('height')
        return rect_path(x, y, w, h, f('rx', el.get('ry', '0'))), (x, y, w, h)
    if tag == 'path':
        return el.get('d'), None
    raise ValueError('Element nicht unterstützt: ' + tag)


def convert(src, dst, width_dp=None):
    root = ET.parse(src).getroot()
    minx, miny, w, h = [float(x) for x in root.get('viewBox').split()]

    defs = {}
    for el in root.iter():
        if el.get('id'):
            defs[el.get('id')] = el

    def gradient(ref, bbox, attr, opacity):
        g = defs[ref]
        stops = ''.join('<item android:offset="%s" android:color="%s"/>' % (
            fmt(s.get('offset')), color(s.get('stop-color'), float(s.get('stop-opacity', '1')) * opacity)) for s in g)
        x1, y1, x2, y2 = (float(g.get(k, d)) for k, d in (('x1', '0'), ('y1', '0'), ('x2', '1'), ('y2', '0')))
        if g.get('gradientUnits') != 'userSpaceOnUse':
            if bbox is None:
                raise ValueError('Verlauf auf Pfad ohne Rahmen: ' + src)
            bx, by, bw, bh = bbox
            x1, x2 = bx + x1 * bw, bx + x2 * bw
            y1, y2 = by + y1 * bh, by + y2 * bh
        return ('<aapt:attr name="android:%s"><gradient android:type="linear" android:startX="%s" android:startY="%s" '
                'android:endX="%s" android:endY="%s">%s</gradient></aapt:attr>' % (attr, fmt(x1), fmt(y1), fmt(x2), fmt(y2), stops))

    def walk(el, inh, opacity, indent):
        out = []
        for child in el:
            tag = child.tag.replace(NS, '')
            if tag in ('defs', 'metadata', 'title', 'desc'):
                continue
            a = dict(inh)
            for k in INHERIT:
                if child.get(k) is not None:
                    a[k] = child.get(k)
            op = opacity * float(child.get('opacity', '1'))
            blur = child.get('filter') is not None
            transform = child.get('transform')
            clip = child.get('clip-path')
            pad = '    ' * indent
            group_open, group_close, inner = [], [], indent
            if transform:
                # Kette wie „translate(0 -8) rotate(-8 60 70)“: von außen nach innen je eine Gruppe.
                parts = re.findall(r'(translate|rotate)\(([^)]*)\)', transform)
                if not parts or re.sub(r'(translate|rotate)\([^)]*\)', '', transform).strip():
                    raise ValueError('Transformation nicht unterstützt: ' + transform)
                for kind, args in parts:
                    v = [float(x) for x in re.split(r'[\s,]+', args.strip())]
                    gpad = '    ' * inner
                    if kind == 'translate':
                        group_open.append('%s<group android:translateX="%s" android:translateY="%s">' % (
                            gpad, fmt(v[0]), fmt(v[1] if len(v) > 1 else 0)))
                    else:
                        group_open.append('%s<group android:rotation="%s" android:pivotX="%s" android:pivotY="%s">' % (
                            gpad, fmt(v[0]), fmt(v[1] if len(v) > 1 else 0), fmt(v[2] if len(v) > 2 else 0)))
                    group_close.insert(0, '%s</group>' % gpad)
                    inner += 1
            if clip:
                cp = defs[clip[5:-1]]
                d = ' '.join(shape(c)[0] for c in cp)
                ipad = '    ' * inner
                group_open.append('%s<group>' % ipad)
                group_open.append('%s    <clip-path android:pathData="%s"/>' % (ipad, d))
                group_close.insert(0, '%s</group>' % ipad)
                inner += 1
            if tag == 'g':
                body = walk(child, a, op, inner)
            else:
                d, bbox = shape(child)
                p = '    ' * inner
                attrs, kids = ['android:pathData="%s"' % d], []
                fill = a.get('fill', '#000000')
                if blur:  # Leuchten nachbilden: kein Weichzeichner in VectorDrawables
                    op *= 0.45
                if fill != 'none':
                    fo = op * float(a.get('fill-opacity', '1'))
                    if fill.startswith('url('):
                        kids.append(gradient(fill[5:-1], bbox, 'fillColor', fo))
                    else:
                        attrs.append('android:fillColor="%s"' % color(fill, fo))
                stroke = a.get('stroke')
                if stroke and stroke != 'none':
                    so = op * float(a.get('stroke-opacity', '1'))
                    if stroke.startswith('url('):
                        kids.append(gradient(stroke[5:-1], bbox, 'strokeColor', so))
                    else:
                        attrs.append('android:strokeColor="%s"' % color(stroke, so))
                    sw = float(a.get('stroke-width', '1')) * (1.6 if blur else 1)
                    attrs.append('android:strokeWidth="%s"' % fmt(sw))
                    if a.get('stroke-linecap'):
                        attrs.append('android:strokeLineCap="%s"' % a['stroke-linecap'])
                    if a.get('stroke-linejoin'):
                        attrs.append('android:strokeLineJoin="%s"' % a['stroke-linejoin'])
                a_s = ('\n%s    ' % p).join(attrs)
                if kids:
                    body = ['%s<path\n%s    %s>\n%s    %s\n%s</path>' % (p, p, a_s, p, ('\n%s    ' % p).join(kids), p)]
                else:
                    body = ['%s<path\n%s    %s />' % (p, p, a_s)]
            out += group_open + body + group_close
        return out

    root_attrs = {k: root.get(k) for k in INHERIT if root.get(k) is not None}
    lines = walk(root, root_attrs, 1.0, 1)
    wdp = float(width_dp) if width_dp else w
    hdp = wdp * h / w
    body = '\n'.join(lines)
    if minx or miny:
        body = '    <group android:translateX="%s" android:translateY="%s">\n%s\n    </group>' % (fmt(-minx), fmt(-miny), body)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Erzeugt aus %s mit design/abzeichen/svg2vd_full.py -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="%sdp"\n    android:height="%sdp"\n'
           '    android:viewportWidth="%s"\n    android:viewportHeight="%s">\n%s\n</vector>\n') % (
        src.replace('\\', '/').split('/')[-1], fmt(wdp), fmt(hdp), fmt(w), fmt(h), body)
    if 'aapt:attr' not in xml:
        xml = xml.replace('\n    xmlns:aapt="http://schemas.android.com/aapt"', '')
    open(dst, 'w', encoding='utf-8').write(xml)
    return len(lines)


if __name__ == '__main__':
    print(convert(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None))
