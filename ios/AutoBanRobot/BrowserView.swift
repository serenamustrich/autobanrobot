import SwiftUI
import UIKit
import UniformTypeIdentifiers
import WebKit

struct BrowserView: UIViewRepresentable {
    @ObservedObject var state: AppState

    func makeCoordinator() -> Coordinator { Coordinator(state: state) }

    func makeUIView(context: Context) -> WKWebView {
        let content = WKUserContentController()
        content.add(context.coordinator, name: "AutoBanBridge")
        content.addUserScript(WKUserScript(
            source: Self.bridgeScript,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        ))
        if let source = Bundle.main.url(forResource: "injected", withExtension: "js"),
           let injected = try? String(contentsOf: source) {
            content.addUserScript(WKUserScript(
                source: injected,
                injectionTime: .atDocumentEnd,
                forMainFrameOnly: true
            ))
        }

        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.userContentController = content
        let webView = SelectionKeywordWebView(frame: .zero, configuration: configuration)
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 26_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/150.0.7871.47 Mobile/15E148 Safari/604.1"
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        context.coordinator.attach(webView)
        context.coordinator.installBackGestures(on: webView)
        UIMenuController.shared.menuItems = [
            UIMenuItem(title: "添加屏蔽关键词", action: #selector(SelectionKeywordWebView.addSelectionToKeywords(_:)))
        ]
        webView.load(URLRequest(url: URL(string: "https://x.com/home")!))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.state = state
    }

    static let bridgeScript = """
    (() => {
      if (window.__AUTOBANROBOT_IOS_BRIDGE__) return;
      window.__AUTOBANROBOT_IOS_BRIDGE__ = true;
      const send = (type, payload) => {
        try { window.webkit.messageHandlers.AutoBanBridge.postMessage({ type, payload }); } catch (_) {}
      };
      window.__AUTOBANROBOT_MOBILE__ = true;
      window.AutoBanBridge = {
        enqueueBlock: payload => send('enqueue', String(payload || '')),
        updateAuth: (bearer, csrf) => send('auth', { bearer: String(bearer || ''), csrf: String(csrf || '') }),
        updateViewerUsername: username => send('viewer', String(username || '')),
        updateSelectedText: text => send('selection', String(text || '')),
        reportScanDiagnostic: diagnostic => send('diagnostic', String(diagnostic || ''))
      };
      window.addEventListener('__twblocker_enqueue__', event => {
        const job = event && event.detail;
        if (!job || !job.username) return;
        try { window.AutoBanBridge.enqueueBlock(JSON.stringify(job)); } catch (_) {}
      });
      const emit = (name, value) => {
        const key = String(name || '').toLowerCase();
        if (key === 'authorization' || key === 'x-csrf-token') {
          const current = window.__AUTOBANROBOT_IOS_AUTH__ || {};
          if (key === 'authorization') current.bearer = String(value || '');
          if (key === 'x-csrf-token') current.csrf = String(value || '');
          window.__AUTOBANROBOT_IOS_AUTH__ = current;
          if (current.bearer || current.csrf) window.AutoBanBridge.updateAuth(current.bearer, current.csrf);
        }
      };
      const readHeaders = headers => {
        if (!headers) return;
        if (typeof headers.forEach === 'function') { headers.forEach((value, name) => emit(name, value)); return; }
        if (Array.isArray(headers)) { headers.forEach(pair => emit(pair && pair[0], pair && pair[1])); return; }
        Object.entries(headers).forEach(([name, value]) => emit(name, value));
      };
      const originalFetch = window.fetch;
      window.fetch = function(input, init) {
        try { if (input instanceof Request) readHeaders(input.headers); readHeaders(init && init.headers); } catch (_) {}
        return originalFetch.apply(this, arguments);
      };
      const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
      XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
        try { emit(name, value); } catch (_) {}
        return originalSetRequestHeader.apply(this, arguments);
      };
      const reportCookieCsrf = () => {
        try {
          const csrf = document.cookie.match(/(?:^|;\\s*)ct0=([^;]+)/)?.[1];
          if (csrf) window.AutoBanBridge.updateAuth('', decodeURIComponent(csrf));
        } catch (_) {}
      };
      reportCookieCsrf();
      let reportSelection = () => {
        const selected = String(window.getSelection && window.getSelection().toString() || '')
          .replace(/\\s+/gu, ' ')
          .trim();
        window.AutoBanBridge.updateSelectedText(selected);
      };
      document.addEventListener('selectionchange', reportSelection);
      window.addEventListener('DOMContentLoaded', reportCookieCsrf, { once: true });
      setTimeout(reportCookieCsrf, 1500);
    })();
    """

    final class SelectionKeywordWebView: WKWebView {
        var selectedKeywordText = ""
        var addSelectedKeyword: ((String) -> Void)?

        override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
            if action == #selector(addSelectionToKeywords(_:)) {
                return !selectedKeywordText.isEmpty
            }
            return super.canPerformAction(action, withSender: sender)
        }

        @objc func addSelectionToKeywords(_ sender: Any?) {
            guard !selectedKeywordText.isEmpty else { return }
            addSelectedKeyword?(selectedKeywordText)
        }
    }

    static let pageBackScript = """
    (() => {
      const selectors = [
        '[data-testid="app-bar-back"]',
        '[data-testid*="back" i]',
        '[aria-label="Back"]',
        '[aria-label="返回"]',
        '[aria-label="上一页"]'
      ];
      const target = selectors
        .flatMap(selector => Array.from(document.querySelectorAll(selector)))
        .find(element => {
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return rect.width > 0 && rect.height > 0 &&
            style.visibility !== 'hidden' && style.display !== 'none';
        });
      if (target) {
        target.click();
        return 1;
      }
      if (window.history.length > 1) {
        window.history.back();
        return 2;
      }
      return 0;
    })();
    """

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler, UIImagePickerControllerDelegate, UINavigationControllerDelegate, UIGestureRecognizerDelegate {
        var state: AppState
        private weak var webView: WKWebView?
        private var filePickerCompletion: (([URL]?) -> Void)?

        init(state: AppState) { self.state = state }

        func attach(_ webView: WKWebView) {
            self.webView = webView
            if let selectableWebView = webView as? SelectionKeywordWebView {
                selectableWebView.addSelectedKeyword = { [weak self] text in
                    self?.state.addKeyword(text)
                }
            }
            state.cookieProvider = { host in await Self.cookieHeader(for: host, store: webView.configuration.websiteDataStore) }
            state.configurationChanged = { [weak self] in self?.applyConfiguration() }
            state.pageScriptExecutor = { [weak webView] script in
                webView?.evaluateJavaScript(script)
            }
        }

        func installBackGestures(on webView: WKWebView) {
            for edge in [UIRectEdge.left, .right] {
                let recognizer = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleBackGesture(_:)))
                recognizer.edges = edge
                recognizer.delegate = self
                recognizer.cancelsTouchesInView = false
                webView.addGestureRecognizer(recognizer)
            }
        }

        @objc private func handleBackGesture(_ recognizer: UIScreenEdgePanGestureRecognizer) {
            guard recognizer.state == .ended, let webView else { return }
            let translation = recognizer.translation(in: webView)
            guard abs(translation.x) >= 72, abs(translation.x) > abs(translation.y) else { return }
            pageBackOrReload(webView)
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let pan = gestureRecognizer as? UIScreenEdgePanGestureRecognizer,
                  let webView else { return true }
            let velocity = pan.velocity(in: webView)
            return abs(velocity.x) > abs(velocity.y)
        }

        private func pageBackOrReload(_ webView: WKWebView) {
            webView.evaluateJavaScript(BrowserView.pageBackScript) { [weak webView] result, _ in
                let action = (result as? NSNumber)?.intValue ?? 0
                guard action == 0, let webView else { return }
                if webView.canGoBack {
                    webView.goBack()
                } else {
                    webView.reload()
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            print("[AutoBanRobot] WebView finished: \(webView.url?.absoluteString ?? "unknown")")
            applyConfiguration()
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping @MainActor @Sendable (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            print("[AutoBanRobot] WebView navigation: \(url.absoluteString)")
            if url.scheme == "x-safari-https" {
                state.setBrowserLoadError("X 请求切换到 Safari")
                decisionHandler(.cancel)
                return
            }
            let allowedHosts = ["x.com", "twitter.com", "twimg.com", "t.co"]
            if allowedHosts.contains(where: { url.host?.hasSuffix($0) == true }) {
                decisionHandler(.allow)
            } else if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            print("[AutoBanRobot] WebView provisional failure at \(webView.url?.absoluteString ?? "unknown"): \(error.localizedDescription)")
            state.setBrowserLoadError(error.localizedDescription)
        }

        @available(iOS 18.4, *)
        func webView(
            _ webView: WKWebView,
            runOpenPanelWith parameters: WKOpenPanelParameters,
            initiatedByFrame frame: WKFrameInfo,
            completionHandler: @escaping @MainActor @Sendable ([URL]?) -> Void
        ) {
            filePickerCompletion = completionHandler
            let sheet = UIAlertController(title: "添加图片", message: nil, preferredStyle: .actionSheet)
            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                sheet.addAction(UIAlertAction(title: "拍照", style: .default) { [weak self] _ in
                    self?.presentImagePicker(source: .camera)
                })
            }
            sheet.addAction(UIAlertAction(title: "从照片中选择", style: .default) { [weak self] _ in
                self?.presentImagePicker(source: .photoLibrary)
            })
            sheet.addAction(UIAlertAction(title: "取消", style: .cancel) { [weak self] _ in
                self?.finishFileSelection(nil)
            })
            topViewController()?.present(sheet, animated: true)
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any], let type = body["type"] as? String else { return }
            switch type {
            case "enqueue": state.enqueue(payload: body["payload"] as? String ?? "")
            case "auth":
                let auth = body["payload"] as? [String: Any] ?? [:]
                state.updateAuth(bearer: auth["bearer"] as? String ?? "", csrf: auth["csrf"] as? String ?? "")
            case "viewer":
                state.updateViewerUsername(body["payload"] as? String ?? "")
            case "selection":
                let text = (body["payload"] as? String ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                (webView as? SelectionKeywordWebView)?.selectedKeywordText = text
            case "diagnostic":
                state.reportScanDiagnostic(body["payload"] as? String ?? "")
            default: break
            }
        }

        private func applyConfiguration() {
            webView?.evaluateJavaScript(state.injectedConfigurationScript())
        }

        private func presentImagePicker(source: UIImagePickerController.SourceType) {
            let picker = UIImagePickerController()
            picker.sourceType = source
            picker.mediaTypes = [UTType.image.identifier]
            picker.delegate = self
            topViewController()?.present(picker, animated: true)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true) { [weak self] in self?.finishFileSelection(nil) }
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let selectedImage = info[.originalImage] as? UIImage
            picker.dismiss(animated: true) { [weak self] in
                guard let self, let selectedImage,
                      let imageData = selectedImage.jpegData(compressionQuality: 0.92) else {
                    self?.finishFileSelection(nil)
                    return
                }
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("x-upload-\(UUID().uuidString).jpg")
                do {
                    try imageData.write(to: url, options: .atomic)
                    self.finishFileSelection([url])
                } catch {
                    self.finishFileSelection(nil)
                }
            }
        }

        private func finishFileSelection(_ urls: [URL]?) {
            let completion = filePickerCompletion
            filePickerCompletion = nil
            completion?(urls)
        }

        private func topViewController(base: UIViewController? = nil) -> UIViewController? {
            let root = base ?? UIApplication.shared.connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow }
                .first?.rootViewController
            if let navigation = root as? UINavigationController { return topViewController(base: navigation.visibleViewController) }
            if let tab = root as? UITabBarController { return topViewController(base: tab.selectedViewController) }
            if let presented = root?.presentedViewController { return topViewController(base: presented) }
            return root
        }

        private static func cookieHeader(for host: String, store: WKWebsiteDataStore) async -> String? {
            await withCheckedContinuation { continuation in
                store.httpCookieStore.getAllCookies { cookies in
                    let value = cookies
                        .filter { cookie in host.hasSuffix(cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: "."))) }
                        .map { "\($0.name)=\($0.value)" }
                        .joined(separator: "; ")
                    continuation.resume(returning: value.isEmpty ? nil : value)
                }
            }
        }
    }
}
