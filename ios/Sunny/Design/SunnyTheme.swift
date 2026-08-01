import SwiftUI

enum SunnyTheme {
    static let orange = Color(red: 0.71, green: 0.28, blue: 0.03)
    static let orangeBright = Color(red: 1.0, green: 0.48, blue: 0.0)
    static let background = Color(uiColor: .systemGroupedBackground)
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let success = Color(red: 0.10, green: 0.50, blue: 0.23)
    static let cornerRadius: CGFloat = 20
}

struct SunnyCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(SunnyTheme.surface, in: RoundedRectangle(cornerRadius: SunnyTheme.cornerRadius))
            .overlay {
                RoundedRectangle(cornerRadius: SunnyTheme.cornerRadius)
                    .stroke(.separator.opacity(0.18), lineWidth: 0.5)
            }
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(SunnyTheme.orange, in: RoundedRectangle(cornerRadius: 16))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.smooth(duration: 0.16), value: configuration.isPressed)
    }
}

extension View {
    func sunnyPrimaryButton() -> some View { buttonStyle(PrimaryButtonStyle()) }
}

