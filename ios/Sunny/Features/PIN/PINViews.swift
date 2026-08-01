import SwiftUI
import UIKit

struct PINLockView: View {
    @EnvironmentObject private var pin: PINManager
    @State private var digits = ""
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "sun.max.fill")
                .font(.system(size: 64))
                .foregroundStyle(SunnyTheme.orangeBright.gradient)
                .accessibilityHidden(true)
            Text("Enter your PIN").font(.largeTitle.bold())
            Text(pin.lockoutRemaining > 0
                 ? "Try again in \(Int(pin.lockoutRemaining.rounded(.up))) seconds."
                 : "Enter your 4-digit PIN to unlock Sunny.")
                .foregroundStyle(errorMessage == nil ? .secondary : .red)
                .contentTransition(.numericText())

            HStack(spacing: 18) {
                ForEach(0..<4, id: \.self) { index in
                    Circle()
                        .fill(index < digits.count ? SunnyTheme.orangeBright : .quaternary)
                        .frame(width: 18, height: 18)
                }
            }
            .accessibilityLabel("\(digits.count) of 4 digits entered")

            LazyVGrid(columns: Array(repeating: GridItem(.fixed(76), spacing: 22), count: 3), spacing: 18) {
                ForEach(1...9, id: \.self) { number in key(String(number)) }
                Color.clear.frame(width: 76, height: 64)
                key("0")
                Button {
                    if !digits.isEmpty { digits.removeLast() }
                } label: {
                    Image(systemName: "delete.backward").frame(width: 76, height: 64)
                }
                .accessibilityLabel("Delete digit")
            }
            Spacer()
        }
        .padding()
        .background(SunnyTheme.background)
    }

    private func key(_ value: String) -> some View {
        Button {
            guard digits.count < 4, pin.lockoutRemaining <= 0 else { return }
            digits.append(value)
            errorMessage = nil
            guard digits.count == 4 else { return }
            let entered = digits
            Task {
                await Task.yield()
                if await pin.verify(entered) {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } else {
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                    errorMessage = "That PIN wasn’t correct."
                    try? await Task.sleep(for: .milliseconds(220))
                    digits = ""
                }
            }
        } label: {
            Text(value)
                .font(.title)
                .foregroundStyle(.primary)
                .frame(width: 76, height: 64)
                .background(.background, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Digit \(value)")
    }
}

struct PINSetupView: View {
    @EnvironmentObject private var pin: PINManager
    @Environment(\.dismiss) private var dismiss
    @State private var first = ""
    @State private var confirmation = ""
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section {
                SecureField("New 4-digit PIN", text: $first)
                    .keyboardType(.numberPad)
                    .onChange(of: first) { _, value in first = String(value.filter(\.isNumber).prefix(4)) }
                SecureField("Confirm PIN", text: $confirmation)
                    .keyboardType(.numberPad)
                    .onChange(of: confirmation) { _, value in confirmation = String(value.filter(\.isNumber).prefix(4)) }
            } footer: {
                Text("This PIN protects access to Sunny. Your encrypted vault also uses iOS Data Protection and a device-bound Keychain key.")
            }
            if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            Section {
                Button("Save PIN") {
                    guard first == confirmation else { errorMessage = "The PINs don’t match."; return }
                    do { try pin.setPIN(first); dismiss() }
                    catch { errorMessage = error.localizedDescription }
                }
                .disabled(first.count != 4 || confirmation.count != 4)
            }
            if pin.isEnabled {
                Section {
                    Button("Remove app PIN", role: .destructive) { pin.clear(); dismiss() }
                }
            }
        }
        .navigationTitle(pin.isEnabled ? "Change PIN" : "Set up PIN")
        .navigationBarTitleDisplayMode(.inline)
    }
}
