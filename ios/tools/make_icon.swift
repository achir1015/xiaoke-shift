// 產生 iPhone App 圖示：小柯臉＋右上角小月曆（同 Android res/drawable 的向量圖，512 座標）。
// 用法：swift tools/make_icon.swift XiaoKeShift/Assets.xcassets/AppIcon.appiconset/icon-1024.png
import AppKit

let out = CommandLine.arguments[1]
let size = 1024
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
ctx.translateBy(x: 0, y: CGFloat(size))
ctx.scaleBy(x: CGFloat(size) / 512, y: -CGFloat(size) / 512)   // 512 座標、y 向下

func rgb(_ v: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: a)
}
func roundRect(_ r: CGRect, _ rad: CGFloat, _ c: CGColor) {
    ctx.addPath(CGPath(roundedRect: r, cornerWidth: rad, cornerHeight: rad, transform: nil)); ctx.setFillColor(c); ctx.fillPath()
}

// 底色（iOS 會自己裁圓角，這裡填滿）
ctx.setFillColor(rgb(0x101522)); ctx.fill(CGRect(x: 0, y: 0, width: 512, height: 512))

// 彎月笑眼
ctx.setStrokeColor(rgb(0xFFFFFF)); ctx.setLineCap(.round); ctx.setLineWidth(28)
for cx in [180.0, 332.0] {
    ctx.addArc(center: CGPoint(x: cx, y: 250), radius: 60, startAngle: .pi, endAngle: 0, clockwise: false)
    ctx.strokePath()
}
// 腮紅
ctx.setFillColor(rgb(0xFF6F91, 0.5))
ctx.fillEllipse(in: CGRect(x: 84, y: 298, width: 88, height: 44))
ctx.fillEllipse(in: CGRect(x: 340, y: 298, width: 88, height: 44))
// 微笑
ctx.setLineWidth(20)
ctx.move(to: CGPoint(x: 206, y: 330)); ctx.addQuadCurve(to: CGPoint(x: 306, y: 330), control: CGPoint(x: 256, y: 376))
ctx.strokePath()

// 右上角小月曆
roundRect(CGRect(x: 348, y: 52, width: 128, height: 108), 12, rgb(0xFFFFFF))
ctx.saveGState()
ctx.clip(to: CGRect(x: 348, y: 52, width: 128, height: 34))
roundRect(CGRect(x: 348, y: 52, width: 128, height: 108), 12, rgb(0xE53935))
ctx.restoreGState()
for (x, y, c) in [(366, 100, 0x1E88E5), (401, 100, 0x1E88E5), (436, 100, 0x1E88E5),
                  (366, 128, 0x1E88E5), (401, 128, 0x1E88E5), (436, 128, 0xFB8C00)] as [(Int, Int, UInt32)] {
    ctx.setFillColor(rgb(c)); ctx.fill(CGRect(x: x, y: y, width: 22, height: 18))
}
ctx.setStrokeColor(rgb(0xFFFFFF)); ctx.setLineWidth(10)
ctx.move(to: CGPoint(x: 384, y: 40)); ctx.addLine(to: CGPoint(x: 384, y: 64))
ctx.move(to: CGPoint(x: 440, y: 40)); ctx.addLine(to: CGPoint(x: 440, y: 64))
ctx.strokePath()

let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("已產生 \(out)")
