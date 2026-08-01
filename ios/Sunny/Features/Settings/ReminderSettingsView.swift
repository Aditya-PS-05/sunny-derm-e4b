import SwiftUI

struct ReminderSettingsView: View {
    @State private var enabled = UserDefaults.standard.bool(forKey: "sunny.reminder.enabled")
    @State private var unit: ReminderUnit = .weeks
    @State private var amount = 4
    @State private var statusMessage: String?
    private let manager = ReminderManager()

    var body: some View {
        Form {
            Section {
                Toggle("Regular skin-check reminder", isOn: $enabled)
            } footer: {
                Text("Sunny schedules reminders locally through iOS. No tracking record is uploaded.")
            }
            if enabled {
                Section("Reminder interval") {
                    Picker("Unit", selection: $unit) {
                        ForEach(ReminderUnit.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Stepper(value: $amount, in: 1...unit.maximum) {
                        Text("Every \(amount) \(amount == 1 ? unit.singular : unit.label.lowercased())")
                    }
                }
                Section {
                    Button("Set reminder") {
                        Task {
                            do {
                                guard try await manager.requestPermission() else {
                                    statusMessage = "Enable notifications for Sunny in iOS Settings."
                                    return
                                }
                                let hours = unit.hours(multipliedBy: amount)
                                let reminder = ReminderItem(
                                    id: "sunny-recurring-check",
                                    title: "Time for a skin check",
                                    body: "Take a few minutes to check your skin and photograph anything new or changed.",
                                    nextTrigger: Date().addingTimeInterval(Double(hours) * 3_600),
                                    intervalHours: hours
                                )
                                try await manager.schedule(reminder)
                                UserDefaults.standard.set(true, forKey: "sunny.reminder.enabled")
                                statusMessage = "Reminder scheduled every \(amount) \(unit.label.lowercased())."
                            } catch { statusMessage = error.localizedDescription }
                        }
                    }
                }
            }
            if let statusMessage { Section { Text(statusMessage).foregroundStyle(.secondary) } }
        }
        .navigationTitle("Reminders")
        .onChange(of: enabled) { _, value in
            if !value {
                manager.cancel(id: "sunny-recurring-check")
                UserDefaults.standard.set(false, forKey: "sunny.reminder.enabled")
            }
        }
    }
}

private enum ReminderUnit: String, CaseIterable, Identifiable {
    case hours, days, weeks, months
    var id: Self { self }
    var label: String { rawValue.capitalized }
    var singular: String { String(rawValue.dropLast()) }
    var maximum: Int { self == .hours ? 720 : self == .days ? 365 : self == .weeks ? 52 : 12 }
    func hours(multipliedBy amount: Int) -> Int {
        switch self {
        case .hours: amount
        case .days: amount * 24
        case .weeks: amount * 24 * 7
        case .months: amount * 24 * 30
        }
    }
}

