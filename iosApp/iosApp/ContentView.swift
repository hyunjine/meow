import ComposeApp
import SwiftUI
import UIKit

/// Compose Multiplatform 화면(composeApp 의 MainViewController)을 SwiftUI 에 올린다.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // safe area(노치 · 홈 인디케이터)는 Compose 쪽 WindowInsets 로 처리한다.
        ComposeView()
            .ignoresSafeArea()
    }
}
