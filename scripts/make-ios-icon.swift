import AppKit
import CoreGraphics

// usage: make_ios_icon <src 1024 macOS icon> <dst 1024 opaque iOS icon>
let args = CommandLine.arguments
let src = NSImage(contentsOfFile: args[1])!
var rect = NSRect(x: 0, y: 0, width: 1024, height: 1024)
let cg = src.cgImage(forProposedRect: &rect, context: nil, hints: nil)!
// macOS 아이콘의 둥근 사각형 본체(투명 여백 제외) 영역
let crop = cg.cropping(to: CGRect(x: 100, y: 96, width: 824, height: 824))!
let size = 1024
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
// 모서리 투명 부분은 아이콘 배경색으로 채운다 (iOS 가 어차피 둥글게 자른다)
let colors = [CGColor(srgbRed: 0.40, green: 0.52, blue: 1.0, alpha: 1), CGColor(srgbRed: 0.18, green: 0.28, blue: 0.85, alpha: 1)] as CFArray
let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colors: colors, locations: [0, 1])!
ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size), end: CGPoint(x: 0, y: 0), options: [])
ctx.interpolationQuality = .high
ctx.draw(crop, in: CGRect(x: 0, y: 0, width: size, height: size))
let out = ctx.makeImage()!
let rep = NSBitmapImageRep(cgImage: out)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
