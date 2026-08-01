import Foundation
import UserNotifications

struct ReminderManager {
    private let center = UNUserNotificationCenter.current()

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func requestPermission() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .badge, .sound])
    }

    func schedule(_ reminder: ReminderItem) async throws {
        let content = UNMutableNotificationContent()
        content.title = reminder.title
        content.body = reminder.body
        content.sound = .default
        if let areaID = reminder.areaID { content.userInfo["areaID"] = areaID.uuidString }

        let trigger: UNNotificationTrigger
        if let intervalHours = reminder.intervalHours {
            trigger = UNTimeIntervalNotificationTrigger(
                timeInterval: TimeInterval(max(1, intervalHours)) * 3_600,
                repeats: true
            )
        } else {
            let seconds = max(60, reminder.nextTrigger.timeIntervalSinceNow)
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        }
        try await center.add(UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger))
    }

    func cancel(id: String) {
        center.removePendingNotificationRequests(withIdentifiers: [id])
    }
}
