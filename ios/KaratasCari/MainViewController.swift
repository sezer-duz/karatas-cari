import UIKit
import WebKit

/// iOS wrapper for the existing PHP/MySQL Karataş Cari application.
/// No credentials are embedded: authentication remains on the HTTPS server.
final class MainViewController: UIViewController, WKNavigationDelegate, WKUIDelegate,
                                WKScriptMessageHandler, BarcodeScanDelegate {
    private static let baseURL = URL(string: "https://cari.ambalajlar.net/")!
    private var webView: WKWebView!
    private let bottomBar = UIToolbar()
    private var pendingBarcodeRequestID: String?
    private var pendingBarcodePage: URL?
    private let backButton = UIBarButtonItem(title: "‹", style: .plain, target: nil, action: nil)
    private let forwardButton = UIBarButtonItem(title: "›", style: .plain, target: nil, action: nil)

    /// Must be restricted to the production HTTPS origin. No arbitrary web page
    /// receives native camera access or script message privileges.
    private func trusted(_ url: URL?) -> Bool {
        guard let url = url, let host = url.host?.lowercased() else { return false }
        return url.scheme?.lowercased() == "https"
            && host == Self.baseURL.host
            && (url.port == nil || url.port == 443)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let content = WKUserContentController()
        content.add(self, name: "karatasBarcode")
        content.addUserScript(WKUserScript(
            source: Self.barcodeBridgeScript, injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        ))
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.userContentController = content
        config.allowsInlineMediaPlayback = true

        webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.translatesAutoresizingMaskIntoConstraints = false
        bottomBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)
        view.addSubview(bottomBar)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: bottomBar.topAnchor),
            bottomBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomBar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
        backButton.target = self
        backButton.action = #selector(goBack)
        forwardButton.target = self
        forwardButton.action = #selector(goForward)
        let reloadButton = UIBarButtonItem(
            barButtonSystemItem: .refresh, target: self, action: #selector(reloadPage)
        )
        bottomBar.items = [
            backButton,
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            forwardButton,
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            reloadButton
        ]
        updateNavigation()
        webView.load(URLRequest(url: Self.baseURL))
    }

    deinit {
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "karatasBarcode")
    }

    @objc private func goBack() {
        if webView.canGoBack { webView.goBack() }
    }
    @objc private func goForward() {
        if webView.canGoForward { webView.goForward() }
    }
    @objc private func reloadPage() { webView.reload() }

    private func updateNavigation() {
        backButton.isEnabled = webView.canGoBack
        forwardButton.isEnabled = webView.canGoForward
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        updateNavigation()
        if trusted(webView.url) {
            webView.evaluateJavaScript(Self.logoutMenuScript, completionHandler: nil)
        }
    }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        pendingBarcodeRequestID = nil
        pendingBarcodePage = nil
        updateNavigation()
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        updateNavigation()
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel); return
        }
        if trusted(url) {
            decisionHandler(.allow)
        } else {
            if ["https", "http", "tel", "mailto"].contains(url.scheme?.lowercased() ?? "") {
                UIApplication.shared.open(url)
            }
            decisionHandler(.cancel)
        }
    }

    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        guard message.name == "karatasBarcode", message.frameInfo.isMainFrame,
              message.frameInfo.securityOrigin.protocol == "https",
              message.frameInfo.securityOrigin.host.lowercased() == Self.baseURL.host,
              trusted(webView.url), pendingBarcodeRequestID == nil,
              let payload = message.body as? String,
              let bytes = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
              object["action"] as? String == "scan",
              let requestID = object["id"] as? String,
              requestID.range(of: "^[A-Za-z0-9_-]{1,50}$", options: .regularExpression) != nil
        else { return }
        pendingBarcodeRequestID = requestID
        pendingBarcodePage = webView.url
        let scanner = BarcodeScannerViewController()
        scanner.delegate = self
        scanner.modalPresentationStyle = .fullScreen
        present(scanner, animated: true)
    }

    func barcodeScanner(_ scanner: BarcodeScannerViewController, didComplete code: String?) {
        scanner.dismiss(animated: true) { [weak self] in
            guard let self = self, let id = self.pendingBarcodeRequestID else { return }
            let original = self.pendingBarcodePage
            self.pendingBarcodeRequestID = nil
            self.pendingBarcodePage = nil
            guard self.trusted(self.webView.url), original == self.webView.url else { return }
            let result: [String: String] = [
                "id": id, "status": code == nil ? "cancel" : "ok",
                "barcode": code ?? "", "detail": ""
            ]
            guard let resultData = try? JSONSerialization.data(withJSONObject: result),
                  let resultJSON = String(data: resultData, encoding: .utf8),
                  let escaped = try? JSONEncoder().encode(resultJSON),
                  let literal = String(data: escaped, encoding: .utf8) else { return }
            self.webView.evaluateJavaScript(
                "window.__karatasNativeBarcodeReply(\(literal));", completionHandler: nil
            )
        }
    }

    // Mirrors the Android KaratasBarcode WebMessage interface used by assets/scanner.js.
    private static let barcodeBridgeScript = #"""
    (function () {
      var listeners = [];
      window.KaratasBarcode = {
        postMessage: function (value) {
          window.webkit.messageHandlers.karatasBarcode.postMessage(String(value));
        },
        addEventListener: function (event, callback) {
          if (event === 'message' && typeof callback === 'function') listeners.push(callback);
        }
      };
      window.__karatasNativeBarcodeReply = function (data) {
        listeners.forEach(function (callback) {
          try { callback({data: data}); } catch (_) {}
        });
      };
    })();
    """#

    // The server's existing CSRF-protected POST logout form is submitted;
    // never clear browser cookies as a replacement for server-side logout.
    private static let logoutMenuScript = #"""
    (function () {
      // PHP v1.36.8 renders a secure logout form directly below Personel Sohbetleri.
      // This bridge also supports older PHP versions without duplicating the form.
      document.querySelectorAll('.sidebar-inner').forEach(function (sidebar) {
        if (sidebar.querySelector('.karatas-ios-logout')) return;
        var links = sidebar.querySelectorAll('a.sidebar-link');
        var messages = null;
        for (var i = 0; i < links.length; i++) {
          if (['Personel Sohbetleri', 'Personel Mesajları'].includes((links[i].textContent || '').trim())) {
            messages = links[i];
            break;
          }
        }
        if (!messages) return;
        var sibling = messages.nextElementSibling;
        if (sibling && sibling.matches('form') && sibling.querySelector('button[aria-label="Çıkış Yap"]')) return;

        var original = sidebar.querySelector('.sidebar-user form[action]');
        if (!original || String(original.getAttribute('method')).toLowerCase() !== 'post') return;
        var url = new URL(original.getAttribute('action'), location.href);
        if (url.origin !== location.origin || url.searchParams.get('route') !== 'logout') return;
        var token = original.querySelector('input[name="_token"]');
        if (!token) return;

        var form = document.createElement('form');
        form.method = 'post';
        form.action = original.action;
        form.className = 'm-0 karatas-ios-logout';
        var hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = '_token';
        hidden.value = token.value;
        var button = document.createElement('button');
        button.type = 'submit';
        button.className = 'sidebar-link w-100 text-start';
        button.setAttribute('aria-label', 'Çıkış Yap');
        var icon = document.createElement('i');
        icon.className = 'bi bi-box-arrow-right';
        var label = document.createElement('span');
        label.textContent = 'Çıkış Yap';
        button.appendChild(icon);
        button.appendChild(label);
        form.appendChild(hidden);
        form.appendChild(button);
        messages.insertAdjacentElement('afterend', form);
      });
    })();
    """#
}
