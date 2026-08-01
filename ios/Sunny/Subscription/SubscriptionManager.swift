import Foundation
import StoreKit
import Combine

@MainActor
final class SubscriptionManager: ObservableObject {
    @Published private(set) var products: [Product] = []
    @Published private(set) var hasProAccess = false
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let productIDs: Set<String>
    private var updates: Task<Void, Never>?

    init(configuration: AppConfiguration = .current) {
        productIDs = [configuration.monthlyProductID, configuration.annualProductID]
        updates = observeTransactions()
        Task { await refresh() }
    }

    deinit { updates?.cancel() }

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            products = try await Product.products(for: productIDs).sorted { lhs, rhs in
                lhs.price < rhs.price
            }
            await refreshEntitlement()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func purchase(_ product: Product) async {
        isLoading = true
        defer { isLoading = false }
        do {
            switch try await product.purchase() {
            case let .success(result):
                let transaction = try verified(result)
                await transaction.finish()
                await refreshEntitlement()
            case .pending:
                errorMessage = "The purchase is waiting for approval."
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func restore() async {
        do {
            try await AppStore.sync()
            await refreshEntitlement()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func observeTransactions() -> Task<Void, Never> {
        Task { [weak self] in
            for await update in Transaction.updates {
                guard let self else { return }
                if let transaction = try? self.verified(update) {
                    await transaction.finish()
                    await self.refreshEntitlement()
                }
            }
        }
    }

    private func refreshEntitlement() async {
        var entitled = false
        for await result in Transaction.currentEntitlements {
            guard let transaction = try? verified(result),
                  productIDs.contains(transaction.productID),
                  transaction.revocationDate == nil else { continue }
            if let expiration = transaction.expirationDate, expiration <= Date() { continue }
            entitled = true
        }
        hasProAccess = entitled
    }

    private func verified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case let .verified(value): value
        case .unverified: throw StoreError.failedVerification
        }
    }
}

private enum StoreError: LocalizedError {
    case failedVerification
    var errorDescription: String? { "The App Store purchase could not be verified." }
}
