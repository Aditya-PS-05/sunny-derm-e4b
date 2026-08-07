import PhotosUI
import SwiftUI
import UIKit

struct CaptureFlow: View {
    var targetAreaID: UUID?
    var presetBodyPart: BodyPart?
    var onSaved: (() -> Void)?
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            CaptureSourceView(targetAreaID: targetAreaID, presetBodyPart: presetBodyPart)
                .navigationDestination(isPresented: Binding(
                    get: { app.captureDraft != nil },
                    set: { if !$0 { app.captureDraft = nil } }
                )) {
                    ReviewPhotoView(onFinished: { onSaved?(); dismiss() })
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { app.captureDraft = nil; dismiss() }
                    }
                }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled(app.analysisProgress == .cloud || app.analysisProgress == .onDevice)
    }
}

private struct CaptureSourceView: View {
    @EnvironmentObject private var app: AppModel
    let targetAreaID: UUID?
    let presetBodyPart: BodyPart?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var showingCamera = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(targetAreaID == nil ? "Add a clear, close photo of one visible area." : "Match the previous photo’s distance, angle and lighting.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 4)

                actionCard(
                    icon: "camera.fill",
                    title: "Take a photo",
                    subtitle: "Use live framing and lighting guidance"
                ) { showingCamera = true }

                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    captureCard(icon: "photo.on.rectangle", title: "Choose from library", subtitle: "Use an existing photo from this device")
                }
                .buttonStyle(.plain)
                .onChange(of: selectedPhoto) { _, item in
                    guard let item else { return }
                    Task {
                        do {
                            guard let data = try await item.loadTransferable(type: Data.self),
                                  let image = UIImage(data: data) else { throw AnalysisError.unreadable }
                            begin(image)
                        } catch { errorMessage = error.localizedDescription }
                    }
                }

                if presetBodyPart == nil && targetAreaID == nil {
                    NavigationLink {
                        GuidedBodyCheckView()
                    } label: {
                        captureCard(
                            icon: "figure.arms.open",
                            title: "Guided body-area check",
                            subtitle: "Work through locations with framing guidance"
                        )
                    }
                    .buttonStyle(.plain)
                }

                SunnyCard {
                    Label("Photo tips", systemImage: "viewfinder")
                        .font(.headline)
                        .foregroundStyle(SunnyTheme.orange)
                    Text("Use even light, hold the phone steady, fill the frame with one spot and avoid filters or digital zoom.")
                        .foregroundStyle(.secondary)
                        .padding(.top, 4)
                }
            }
            .padding()
        }
        .background(SunnyTheme.background)
        .navigationTitle(targetAreaID == nil ? "Add photo" : "Add follow-up")
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(isPresented: $showingCamera) {
            CameraPicker { result in
                showingCamera = false
                switch result {
                case let .success(image): begin(image)
                case let .failure(error): errorMessage = error.localizedDescription
                }
            }
            .ignoresSafeArea()
        }
        .alert("Couldn’t open photo", isPresented: Binding(
            get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK") { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func begin(_ image: UIImage) {
        app.beginCapture(image: image, targetAreaID: targetAreaID)
        if let presetBodyPart { app.captureDraft?.bodyPart = presetBodyPart }
    }

    private func actionCard(icon: String, title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { captureCard(icon: icon, title: title, subtitle: subtitle) }
            .buttonStyle(.plain)
    }

    private func captureCard(icon: String, title: String, subtitle: String) -> some View {
        SunnyCard {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(SunnyTheme.orange)
                    .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
    }
}

private struct ReviewPhotoView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    let onFinished: () -> Void
    @State private var saving = false
    @State private var sizeText = ""

    private var previousObservation: Observation? {
        guard let target = app.captureDraft?.targetAreaID else { return nil }
        return app.areas.first(where: { $0.id == target })?.latest
    }

    var body: some View {
        Group {
            if let draft = app.captureDraft {
                ScrollView {
                    VStack(spacing: 14) {
                        Image(uiImage: draft.image)
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: .infinity)
                            .aspectRatio(1, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 22))
                            .accessibilityLabel("Photo being reviewed")

                        SunnyCard {
                            Picker("Photo type", selection: Binding(
                                get: { app.captureDraft?.kind ?? .single },
                                set: { app.captureDraft?.kind = $0 }
                            )) {
                                ForEach(ScanKind.allCases) { Text($0.label).tag($0) }
                            }
                            .pickerStyle(.segmented)

                            Picker("Body area", selection: Binding(
                                get: { app.captureDraft?.bodyPart ?? .shoulder },
                                set: { app.captureDraft?.bodyPart = $0 }
                            )) {
                                ForEach(BodyPart.allCases) { Text($0.locationLine).tag($0) }
                            }
                            .padding(.top, 10)

                            DisclosureGroup("Approximate size (optional)") {
                                HStack {
                                    TextField("Millimetres", text: $sizeText)
                                        .keyboardType(.decimalPad)
                                        .onChange(of: sizeText) { _, value in
                                            let normalized = value.replacingOccurrences(of: ",", with: ".")
                                            app.captureDraft?.approximateSizeMM = Double(normalized)
                                        }
                                    Text("mm").foregroundStyle(.secondary)
                                }
                                Text("A reference-based estimate only—not a clinical measurement.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            .padding(.top, 12)
                        }

                        progressContent

                        if let result = draft.result {
                            if let previousObservation {
                                AnalysisChangesCard(
                                    since: previousObservation.capturedAt,
                                    changes: AnalysisComparison.changes(
                                        previous: previousObservation.analysis,
                                        current: result.analysis
                                    )
                                )
                            }
                            AnalysisCard(analysis: result.analysis)
                        }
                    }
                    .padding()
                }
                .safeAreaInset(edge: .bottom) {
                    Button(buttonTitle) {
                        if draft.result == nil { app.analyzeDraft() }
                        else {
                            saving = true
                            Task {
                                if await app.saveDraft() != nil { onFinished() }
                                saving = false
                            }
                        }
                    }
                    .sunnyPrimaryButton()
                    .disabled(isBusy)
                    .padding()
                    .background(.bar)
                }
            } else {
                EmptyStateView(icon: "photo", title: "No photo selected", message: "Go back and choose a photo.")
            }
        }
        .background(SunnyTheme.background)
        .navigationTitle("Review photo")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Retake") { app.cancelAnalysis(); app.captureDraft = nil; dismiss() }
            }
        }
        .onAppear { if app.analysisProgress == .idle { app.analyzeDraft() } }
        .onDisappear { if app.captureDraft == nil { app.cancelAnalysis() } }
    }

    @ViewBuilder private var progressContent: some View {
        switch app.analysisProgress {
        case .idle:
            EmptyView()
        case .preparing:
            statusCard(title: "Preparing photo…", subtitle: "Optimising the image securely.")
        case .cloud:
            statusCard(title: "Sending to Sunny AI Cloud…", subtitle: "Keep Sunny open while analysis finishes.")
        case .onDevice:
            statusCard(title: "Analysing privately on this iPhone…", subtitle: "The photo does not leave your device.")
        case .ready:
            EmptyView()
        case let .failed(message):
            SunnyCard {
                Label("Couldn’t read this image", systemImage: "exclamationmark.triangle")
                    .font(.headline)
                Text(message).font(.subheadline).foregroundStyle(.secondary).padding(.top, 4)
                Button("Try again") { app.analyzeDraft() }.padding(.top, 6)
            }
        }
    }

    private func statusCard(title: String, subtitle: String) -> some View {
        SunnyCard {
            HStack(spacing: 14) {
                ProgressView().tint(SunnyTheme.orange)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Button("Cancel analysis") { app.cancelAnalysis() }
                .font(.subheadline)
                .padding(.top, 8)
        }
    }

    private var isBusy: Bool {
        saving || app.analysisProgress == .preparing || app.analysisProgress == .cloud || app.analysisProgress == .onDevice
    }

    private var buttonTitle: String {
        if saving { return "Saving…" }
        if app.captureDraft?.result != nil { return app.captureDraft?.targetAreaID == nil ? "Save photo" : "Save follow-up" }
        return "Analyse photo"
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    let completion: (Result<UIImage, Error>) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(completion: completion) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completion: (Result<UIImage, Error>) -> Void
        init(completion: @escaping (Result<UIImage, Error>) -> Void) { self.completion = completion }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage { completion(.success(image)) }
            else { completion(.failure(AnalysisError.unreadable)) }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            completion(.failure(CancellationError()))
        }
    }
}
