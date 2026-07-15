from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
ERRORS: list[str] = []


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        ERRORS.append(f"Missing required file: {relative_path}")
        return ""


def require_file(relative_path: str) -> None:
    if not (ROOT / relative_path).is_file():
        ERRORS.append(f"Missing required file: {relative_path}")


def require_contains(relative_path: str, expected: str) -> None:
    if expected not in read_text(relative_path):
        ERRORS.append(f"{relative_path} must contain: {expected}")


for required in (
    "android/settings.gradle.kts",
    "android/build.gradle.kts",
    "android/app/build.gradle.kts",
    "android/app/src/main/AndroidManifest.xml",
    "android/app/src/main/java/com/chloemlla/synapse/mobile/MainActivity.kt",
    "android/app/src/legacy/AndroidManifest.xml",
    "android/app/src/production/AndroidManifest.xml",
    "android/app/proguard-rules.pro",
    ".github/workflows/synapse-android.yml",
):
    require_file(required)

main_source_root = ROOT / "android/app/src/main"
java_files = sorted(path.relative_to(ROOT).as_posix() for path in main_source_root.rglob("*.java"))
if java_files:
    ERRORS.append("Android main source must stay Kotlin-only; Java files found: " + ", ".join(java_files))

require_contains("android/app/src/main/AndroidManifest.xml", 'android:usesCleartextTraffic="false"')
require_contains("android/app/build.gradle.kts", "isMinifyEnabled = true")
require_contains("android/app/build.gradle.kts", "isShrinkResources = true")
require_contains("android/app/build.gradle.kts", 'getDefaultProguardFile("proguard-android-optimize.txt")')
require_contains("android/app/build.gradle.kts", 'applicationId = "com.chloemlla.synapse.mobile"')
require_contains("android/app/build.gradle.kts", 'applicationId = "com.synapse.mobile"')
require_contains("android/app/build.gradle.kts", 'create("legacy")')
require_contains("android/app/build.gradle.kts", 'create("production")')

require_contains("android/settings.gradle.kts", "maven.pkg.github.com/Chloemlla/Project-Lumen")
require_contains("android/app/build.gradle.kts", 'implementation("com.chloemlla.lumen:lumen-crash:0.1.0")')
require_contains("android/app/src/main/java/com/chloemlla/synapse/mobile/SynapseApplication.kt", "LumenCrash.install")
require_contains("android/app/src/main/java/com/chloemlla/synapse/mobile/MainActivity.kt", "LumenCrashReportScreen")

require_contains("android/app/src/legacy/AndroidManifest.xml", "com.synapse.mobile.migration")
require_contains("android/app/src/legacy/AndroidManifest.xml", "MigrationConfigProvider")
require_contains(".github/workflows/synapse-android.yml", "gradle testProductionDebugUnitTest")
require_contains(".github/workflows/synapse-android.yml", "LUMEN_CRASH_READ_PACKAGES_TOKEN")
require_contains(".github/workflows/synapse-android.yml", "packages: read")

require_contains(".github/workflows/synapse-android.yml", "gradle lintProductionDebug")
require_contains(".github/workflows/synapse-android.yml", "gradle assembleProductionRelease assembleLegacyRelease")

forbidden_tokens = (
    "SYNAPSE_CERTIFICATE_PINS",
    "SYNAPSE_REQUIRE_CERTIFICATE_PINS",
    "CertificatePinner",
    "CertificatePinPolicy",
)
scan_roots = (
    ROOT / "android",
    ROOT / ".github/workflows",
)
for scan_root in scan_roots:
    for path in scan_root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".kts", ".yml", ".yaml", ".md", ".xml"}:
            continue
        relative = path.relative_to(ROOT).as_posix()
        content = path.read_text(encoding="utf-8")
        for token in forbidden_tokens:
            if token in content:
                ERRORS.append(f"Client certificate pinning token remains in {relative}: {token}")

if ERRORS:
    for error in ERRORS:
        print(f"::error::{error}")
    sys.exit(1)

print("Synapse Android policy checks passed.")
