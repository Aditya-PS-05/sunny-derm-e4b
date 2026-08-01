import SwiftUI

struct OnboardingView: View {
    let onFinish: () -> Void
    @State private var page = 0

    private let pages: [(String, String, String)] = [
        ("sun.max.fill", "Welcome to Sunny", "Photograph and track visible skin changes over time in one private place."),
        ("lock.shield.fill", "Your photos stay protected", "Photos and tracking records are encrypted on this device. Cloud analysis is used only when you choose it."),
        ("waveform.path.ecg", "Descriptions—not diagnoses", "Sunny describes visible features. It cannot diagnose a condition or replace a qualified clinician."),
    ]

    var body: some View {
        VStack(spacing: 20) {
            TabView(selection: $page) {
                ForEach(Array(pages.enumerated()), id: \.offset) { index, item in
                    VStack(spacing: 24) {
                        Spacer()
                        Image(systemName: item.0)
                            .font(.system(size: 76, weight: .medium))
                            .foregroundStyle(SunnyTheme.orangeBright.gradient)
                            .symbolEffect(.pulse, options: .repeating, isActive: index == page)
                            .accessibilityHidden(true)
                        Text(item.1)
                            .font(.largeTitle.bold())
                            .multilineTextAlignment(.center)
                        Text(item.2)
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                        Spacer()
                    }
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            Button(page == pages.count - 1 ? "I understand" : "Continue") {
                if page == pages.count - 1 { onFinish() }
                else { withAnimation(.smooth) { page += 1 } }
            }
            .sunnyPrimaryButton()
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .background(SunnyTheme.background)
    }
}
