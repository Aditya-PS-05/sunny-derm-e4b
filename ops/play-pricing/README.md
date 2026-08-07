# Sunny Pro regional pricing

`regional-prices.csv` is the launch input for the Google Play subscription:

- Product ID: `sunny_pro`
- Auto-renewing base plan `monthly`: P1M
- Auto-renewing base plan `annual`: P1Y
- Monthly new-customer offer `intro-month`: one paid month at the regional
  introductory price, tagged `sunny-pro-intro`, followed by the monthly price

The Android app reads `ProductDetails` from Google Play, so every button displays
the buyer's actual localized price. No currency or price is hard-coded in the APK.

Apply the CSV values in Play Console under **Monetize → Products → Subscriptions
→ sunny_pro → Base plans and offers → Price and availability**. The Console/API
operation requires the owner's Play credentials and intentionally is not run by
the repository build. Review tax-inclusive checkout prices and valid local price
steps before publishing. Use Play's automatic conversion for unlisted regions,
then review conversion and retention by country after 8–12 weeks.

The annual price is approximately 37–40% below twelve monthly payments. Do not
create a zero-cost trial for the downloadable model: the paid introductory month
establishes a verified purchase before the private 393 MiB pack is exposed.
