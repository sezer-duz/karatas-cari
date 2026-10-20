import UIKit
import AVFoundation

protocol BarcodeScanDelegate: AnyObject {
    func barcodeScanner(_ scanner: BarcodeScannerViewController, didComplete code: String?)
}

/// Camera-based barcode reader for iOS 15+, no Firebase or external SDK.
final class BarcodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    weak var delegate: BarcodeScanDelegate?
    private let captureSession = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    private let sessionQueue = DispatchQueue(label: "com.cari.ambalajlar.ios.camera")
    private let statusLabel = UILabel()
    private var completed = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        let closeButton = UIButton(type: .system)
        closeButton.setTitle("Kapat", for: .normal)
        closeButton.titleLabel?.font = .boldSystemFont(ofSize: 17)
        closeButton.setTitleColor(.white, for: .normal)
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        closeButton.layer.cornerRadius = 10
        closeButton.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = "Kamerayı barkoda doğrultun"
        statusLabel.textColor = .white
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(closeButton)
        view.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 14),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 90),
            closeButton.heightAnchor.constraint(equalToConstant: 44),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            statusLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -30)
        ])
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
                DispatchQueue.main.async {
                    if allowed { self?.configureCamera() }
                    else { self?.statusLabel.text = "Kamera izni verilmedi. Ayarlardan izin verebilirsiniz." }
                }
            }
        default:
            statusLabel.text = "Kamera izni kapalı. iPhone Ayarlarından açabilirsiniz."
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.layer.bounds
    }

    private func configureCamera() {
        guard let camera = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: camera) else {
            statusLabel.text = "Bu cihazda kamera kullanılamıyor."
            return
        }

        captureSession.beginConfiguration()
        guard captureSession.canAddInput(input) else {
            captureSession.commitConfiguration()
            statusLabel.text = "Kamera girişine ulaşılamadı."
            return
        }
        captureSession.addInput(input)

        let metadata = AVCaptureMetadataOutput()
        guard captureSession.canAddOutput(metadata) else {
            captureSession.commitConfiguration()
            statusLabel.text = "Barkod tarayıcı başlatılamadı."
            return
        }
        captureSession.addOutput(metadata)
        metadata.setMetadataObjectsDelegate(self, queue: .main)
        let formats: [AVMetadataObject.ObjectType] = [
            .ean8, .ean13, .code128, .code39, .code93, .upce,
            .qr, .pdf417, .dataMatrix, .aztec, .itf14, .interleaved2of5
        ]
        metadata.metadataObjectTypes = formats.filter { metadata.availableMetadataObjectTypes.contains($0) }
        captureSession.commitConfiguration()

        let layer = AVCaptureVideoPreviewLayer(session: captureSession)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.layer.bounds
        view.layer.insertSublayer(layer, at: 0)
        preview = layer
        sessionQueue.async { [weak self] in self?.captureSession.startRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard !completed,
              let barcode = metadataObjects.compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
                                          .first(where: { !($0.stringValue ?? "").isEmpty }),
              let value = barcode.stringValue else { return }
        completed = true
        sessionQueue.async { [weak self] in self?.captureSession.stopRunning() }
        delegate?.barcodeScanner(self, didComplete: value)
    }

    @objc private func cancel() {
        guard !completed else { return }
        completed = true
        sessionQueue.async { [weak self] in self?.captureSession.stopRunning() }
        delegate?.barcodeScanner(self, didComplete: nil)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { [weak self] in
            if self?.captureSession.isRunning == true { self?.captureSession.stopRunning() }
        }
    }
}
