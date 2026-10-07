// Meow Notifier — Meow 앱 아이콘으로 macOS 알림을 띄우는 작은 헬퍼 앱.
//
// 사용법: MeowNotifier --title T [--subtitle S] [--body B] [--url U]
//   - 처음 실행 시 알림 권한(alert + sound)을 요청하고, 알림을 등록한 뒤 종료한다.
//   - --url 이 있으면 userInfo 에 담아 두고, 사용자가 알림을 클릭하면 macOS 가 이 앱을 다시 실행해
//     delegate 로 응답을 전달한다 → 그 URL 을 기본 브라우저로 연다.
//
// 종료 코드: 0 = 알림 등록됨, 1 = 오류(인자 없음·등록 실패), 2 = 사용자가 알림 권한을 거부함.

import Cocoa
import UserNotifications

private let urlKey = "url"

private struct Options {
    var title: String?
    var subtitle: String?
    var body: String?
    var url: String?

    var hasContent: Bool { title != nil || body != nil }

    static func parse(_ args: [String]) -> Options {
        var options = Options()
        var i = 1
        while i < args.count {
            let key = args[i]
            let value = i + 1 < args.count ? args[i + 1] : nil
            switch key {
            case "--title": options.title = value; i += 2
            case "--subtitle": options.subtitle = value; i += 2
            case "--body": options.body = value; i += 2
            case "--url": options.url = value; i += 2
            default: i += 1 // macOS 가 붙이는 -psn_ 같은 인자는 무시한다.
            }
        }
        return options
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()

    func applicationWillFinishLaunching(_ notification: Notification) {
        // 알림 클릭으로 실행된 경우 응답을 받으려면 실행이 끝나기 전에 delegate 를 지정해야 한다.
        center.delegate = self
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let options = Options.parse(CommandLine.arguments)
        if options.hasContent {
            post(options)
        } else {
            // 인자 없이 실행됨 = 알림 클릭으로 재실행. didReceive 가 오지 않으면 잠시 뒤 종료한다.
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { exit(0) }
        }
    }

    private func post(_ options: Options) {
        center.requestAuthorization(options: [.alert, .sound]) { granted, error in
            guard granted else {
                FileHandle.standardError.write("not authorized: \(error?.localizedDescription ?? "denied")\n".data(using: .utf8)!)
                exit(2)
            }
            let content = UNMutableNotificationContent()
            content.title = options.title ?? "Meow"
            if let subtitle = options.subtitle, !subtitle.isEmpty { content.subtitle = subtitle }
            if let body = options.body { content.body = body }
            content.sound = .default
            if let url = options.url, !url.isEmpty { content.userInfo = [urlKey: url] }

            let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
            self.center.add(request) { error in
                if let error = error {
                    FileHandle.standardError.write("failed to post: \(error.localizedDescription)\n".data(using: .utf8)!)
                    exit(1)
                }
                // 전달될 시간을 잠깐 준 뒤 종료한다.
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { exit(0) }
            }
        }
    }

    // 헬퍼가 떠 있는 동안 도착한 알림도 배너로 보여 준다.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    // 알림 클릭 → 담아 둔 URL 을 연다.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier,
           let raw = response.notification.request.content.userInfo[urlKey] as? String,
           let url = URL(string: raw),
           url.scheme == "https" || url.scheme == "http" {
            NSWorkspace.shared.open(url)
        }
        completionHandler()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { exit(0) }
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
