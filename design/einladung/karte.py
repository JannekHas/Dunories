"""Vorschaubild für Einladungslinks (WhatsApp & Co., Open Graph 1200×630) im Dunories-Stil."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1200, 630
FONT = 'app/src/main/res/font/bricolage_grotesque.ttf'

def font(size, weight='Bold'):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_name(weight)
    except Exception:
        pass
    return f

img = Image.new('RGB', (W, H), '#14141B')
# weiche Farbflecken wie im App-Hintergrund
glow = Image.new('RGB', (W, H), '#14141B')
d = ImageDraw.Draw(glow)
d.ellipse((-200, -260, 520, 360), fill='#3B1F5E')
d.ellipse((760, 300, 1420, 900), fill='#0E4A55')
glow = glow.filter(ImageFilter.GaussianBlur(140))
img = Image.blend(img, glow, 0.9)

logo = Image.open('design/logo/playstore_512.png').convert('RGBA').resize((300, 300), Image.LANCZOS)
mask = Image.new('L', (300, 300), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, 300, 300), 68, fill=255)
img.paste(logo, (90, 165), mask)

d = ImageDraw.Draw(img)
d.text((450, 175), 'Komm zu', font=font(62, 'Medium'), fill='#A3A3B8')
d.text((446, 240), 'Dunories', font=font(118), fill='#F2F2F7')
d.text((452, 385), 'Kalorien & Makros tracken –', font=font(40, 'Regular'), fill='#F2F2F7')
d.text((452, 435), 'mit Foto, Serien & Freunden', font=font(40, 'Regular'), fill='#F2F2F7')
# Neon-Akzent
d.rounded_rectangle((452, 505, 700, 515), 5, fill='#22D3EE')
d.rounded_rectangle((712, 505, 800, 515), 5, fill='#FFD400')
d.rounded_rectangle((812, 505, 860, 515), 5, fill='#3DDC84')

img.save('backend/ai-worker/src/invite-card.png', optimize=True)
print('ok')
