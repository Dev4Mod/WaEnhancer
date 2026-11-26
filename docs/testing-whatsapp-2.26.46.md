# WhatsApp 2.26.46 validation

Device validation: Samsung Galaxy S23 (SM-S911B), Android 16 / API 36,
KernelSU root, Vector, WhatsApp **2.26.46.76** (version code 264607600).

## Changes exercised

- Recognize the new instance props resolver while preserving the legacy static
  resolver. Match the host's Kotlin Function1 by name because the module and
  WhatsApp can load distinct copies of Kotlin.
- Handle the filter header owned by ConversationsFragmentKt without looking for
  the removed private height method on ConversationsFragment. Zero the measured
  height of the RecyclerView header containing the bound filter root or container,
  including the uninflated ViewStub state. Preserve other rows and normal spacing.
- Add the consumer WhatsApp 2.26.46.xx family to the supported version list.
  WhatsApp Business is not added: it was not installed on the test device.

## Automated validation

```text
gradlew :app:testWhatsappDebugUnitTest :app:assembleWhatsappDebug
```

The regression tests cover the legacy static signature, the new instance
signature, rejection of unrelated signatures, and a separate host Kotlin class
loader. The existing Google Translate settings tests also run.

## Device validation

The device build used a separate `.compat` application ID and a test-only
allowance in the package-name guard so it could run without the upstream signing
key. Neither override is part of the proposed production changes. Only this
WaEnhancer package was left installed and scoped to the consumer WhatsApp in
user 0; no system-server scope was added. The previous test package was removed.

1. Launch with `bypass_version_check=false`. The module reports 2.26.46.76 as
   supported and initializes all 68 registered plugins. Fresh process logs show
   the instance resolver being hooked, with no resolver fallback, load exceptions,
   or missing legacy filter-height method.
2. Enable chat filters, restart the module and WhatsApp, and inspect the visible
   hierarchy: the filters occupy y=470..614 and the first chat begins at y=638.
3. Disable chat filters and restart: the filter chips disappear and the first
   chat begins at y=470, reclaiming the complete 168 px header row. Repeat two
   cold launches with filters already disabled to check the uninflated ViewStub
   path; an attach-only fix does not handle this state.
4. Enable filters again. Tap All, Unread, Groups, and All. The displayed row set
   changes for Unread and Groups; returning to All restores the initial row set.
   Comparisons use local hashes rather than exporting chat names or message text.
5. Return to All, then leave filters hidden as selected by the device owner.
   Confirm only the test WaEnhancer is
   injected into the newly started WhatsApp process.

This is startup and filter behavior validation on one consumer build, not an
end-to-end certification of every feature. No messages were sent and no calls
were placed. Receipt behavior, status changes, media operations, Business, other
2.26.46 patch builds, and older WhatsApp versions were not exercised on-device.
