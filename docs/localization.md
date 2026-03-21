# Localization Guidelines

## French resources

- File: `app/src/main/res/values-fr/strings.xml`
- Keep French accents and apostrophes intact (`é`, `à`, `ç`, `l'application`, etc.).
- Do not transliterate French text to ASCII.

## Automated guard

The build includes `checkFrenchStringsQuality`, which runs before `preBuild` and fails when it detects common unaccented French forms or likely missing apostrophes.

If the check fails, correct the strings in `values-fr/strings.xml` and rerun the build.
