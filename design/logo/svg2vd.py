"""SVG (eingeschränkte Untermenge: path/circle, linear/radialGradient userSpaceOnUse) -> Android VectorDrawable."""
import sys
import xml.etree.ElementTree as ET

NS = '{http://www.w3.org/2000/svg}'


def hexcolor(c, opacity=1.0):
    c = c.strip()
    if c.lower() in ('#fff', '#ffffff'):
        c = '#FFFFFF'
    if not c.startswith('#') or len(c) != 7:
        raise ValueError('Farbe nicht unterstützt: ' + c)
    a = round(max(0.0, min(1.0, opacity)) * 255)
    return '#%02X%s' % (a, c[1:].upper())


def circle_path(cx, cy, r):
    return 'M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 %s,0Z' % (
        fmt(cx - r), fmt(cy), fmt(r), fmt(r), fmt(2 * r), fmt(r), fmt(r), fmt(-2 * r))


def fmt(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return s if s != '-0' else '0'


def convert(src, dst, tint=None):
    root = ET.parse(src).getroot()
    vb = [float(x) for x in root.get('viewBox').split()]
    minx, miny, w, h = vb
    grads = {}
    for g in root.iter():
        tag = g.tag.replace(NS, '')
        if tag in ('linearGradient', 'radialGradient'):
            assert g.get('gradientUnits') == 'userSpaceOnUse', src
            stops = []
            for s in g:
                off = float(s.get('offset'))
                op = float(s.get('stop-opacity', '1'))
                stops.append((off, hexcolor(s.get('stop-color'), op)))
            grads[g.get('id')] = (tag, dict(g.attrib), stops)

    def gradient_xml(ref, attr):
        tag, a, stops = grads[ref]
        items = ''.join('<item android:offset="%s" android:color="%s"/>' % (fmt(o), c) for o, c in stops)
        if tag == 'linearGradient':
            head = ('<gradient android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s">'
                    % (a['x1'], a['y1'], a['x2'], a['y2']))
        else:
            head = ('<gradient android:type="radial" android:centerX="%s" android:centerY="%s" android:gradientRadius="%s">'
                    % (a['cx'], a['cy'], a['r']))
        return '<aapt:attr name="android:%s">%s%s</gradient></aapt:attr>' % (attr, head, items)

    out = []
    for el in root:
        tag = el.tag.replace(NS, '')
        if tag in ('defs', 'metadata'):
            continue
        if tag == 'circle':
            d = circle_path(float(el.get('cx')), float(el.get('cy')), float(el.get('r')))
        elif tag == 'path':
            d = el.get('d')
        else:
            raise ValueError('Element nicht unterstützt: ' + tag)
        attrs = ['android:pathData="%s"' % d]
        children = []
        fill = el.get('fill', '#000000')
        if fill != 'none':
            if fill.startswith('url('):
                children.append(gradient_xml(fill[5:-1], 'fillColor'))
            else:
                attrs.append('android:fillColor="%s"' % (tint or hexcolor(fill, float(el.get('fill-opacity', '1')))))
        stroke = el.get('stroke')
        if stroke and stroke != 'none':
            if stroke.startswith('url('):
                children.append(gradient_xml(stroke[5:-1], 'strokeColor'))
            else:
                attrs.append('android:strokeColor="%s"' % (tint or hexcolor(stroke, float(el.get('stroke-opacity', '1')))))
            attrs.append('android:strokeWidth="%s"' % el.get('stroke-width', '1'))
            cap = el.get('stroke-linecap')
            if cap:
                attrs.append('android:strokeLineCap="%s"' % cap)
        attrs_s = '\n        '.join(attrs)
        if children:
            out.append('    <path\n        %s>\n        %s\n    </path>' % (attrs_s, '\n        '.join(children)))
        else:
            out.append('    <path\n        %s />' % attrs_s)

    width_dp = sys.argv[3] if len(sys.argv) > 3 else None
    wdp = float(width_dp) if width_dp else w
    hdp = wdp * h / w
    group_open = group_close = ''
    if minx or miny:
        group_open = '    <group android:translateX="%s" android:translateY="%s">\n' % (fmt(-minx), fmt(-miny))
        group_close = '\n    </group>'
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Erzeugt aus %s (Logo „Ring-o“) -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="%sdp"\n    android:height="%sdp"\n'
           '    android:viewportWidth="%s"\n    android:viewportHeight="%s">\n%s%s%s\n</vector>\n') % (
        src.replace('\\', '/').split('/')[-1], fmt(wdp), fmt(hdp), fmt(w), fmt(h), group_open, '\n'.join(out), group_close)
    if 'aapt:attr' not in xml:
        xml = xml.replace('\n    xmlns:aapt="http://schemas.android.com/aapt"', '')
    open(dst, 'w', encoding='utf-8').write(xml)
    print('ok', dst, len(out), 'Pfade')


if __name__ == '__main__':
    convert(sys.argv[1], sys.argv[2])
