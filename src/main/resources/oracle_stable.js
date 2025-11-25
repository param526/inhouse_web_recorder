(function () {
    const STORAGE_KEY = '__recordedEvents';
    const TEXT_CHANGE_DEBOUNCE_MS = 700;
    const pendingTextChangeTimers = {};   // key -> { timeoutId, rec }

    let lsDirty = false;
    let lsFlushTimer = null;
    const LS_FLUSH_INTERVAL_MS = 1000;

    // 🔑 Global marker to ensure ONLY first navigation gets driver.get()
    const NAV_GET_MARKER = '__recorderNavGetDone__';

    // 🔹 For deduping ojAction + native click
    let lastOjActionTs = 0;
    let lastOjActionButton = null; // oj-button / button.oj-button-button

    function markGlobalNavGet() {
        try {
            if (typeof window.name === 'string') {
                if (window.name.indexOf(NAV_GET_MARKER) === -1) {
                    window.name = window.name
                        ? (window.name + '|' + NAV_GET_MARKER)
                        : NAV_GET_MARKER;
                }
            } else {
                window.name = NAV_GET_MARKER;
            }
        } catch (e) {
            // best-effort only
        }
    }

    function hasGlobalNavGet(url) {
        try {
            if (typeof window.name === 'string' &&
                window.name.indexOf(NAV_GET_MARKER) !== -1) {
                return true;
            }
        } catch (e) {}

        if (typeof url === 'string' &&
            url.indexOf(NAV_GET_MARKER) !== -1) {
            return true;
        }

        return false;
    }

    // ---- Load existing events ----
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        window.__recordedEvents = saved ? JSON.parse(saved) : [];
        if (!Array.isArray(window.__recordedEvents)) {
            window.__recordedEvents = [];
        }
    } catch (e) {
        console.log('[recorder] Failed to read localStorage, starting fresh', e);
        window.__recordedEvents = [];
    }

    if (window.__recordingInstalled) {
        console.log('[recorder] Already installed on this page');
        return;
    }
    window.__recordingInstalled = true;

    window.__currentUrl = window.location.href;
    console.log('[recorder] Installed on', window.location.href);

    function scheduleLocalStorageFlush() {
        if (lsFlushTimer !== null) return;

        lsFlushTimer = setTimeout(function () {
            lsFlushTimer = null;
            if (!lsDirty) return;
            try {
                window.__recordedEvents.sort((a, b) => a.timestamp - b.timestamp);
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents || []));
            } catch (e) {
                console.log('[recorder] Failed to write to localStorage', e);
            }
            lsDirty = false;
        }, LS_FLUSH_INTERVAL_MS);
    }

    function persistEvent(rec) {
        if (!window.__recordedEvents) window.__recordedEvents = [];
        window.__recordedEvents.push(rec);

        window.__recordedEvents.sort((a, b) => a.timestamp - b.timestamp);

        if (rec.type === 'click' || rec.type === 'navigation') {
            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents));
                lsDirty = false;
                if (lsFlushTimer) {
                    clearTimeout(lsFlushTimer);
                    lsFlushTimer = null;
                }
            } catch (e) {
                console.log('[recorder] Failed to write to localStorage', e);
            }
        } else {
            lsDirty = true;
            scheduleLocalStorageFlush();
        }
    }

    // =========================================================
    // 🛠️ HELPER: Text Extraction
    // =========================================================
    function getAccessibleName(t) {
        if (!t) return '';

        const ariaLabel = t.getAttribute('aria-label');
        if (ariaLabel) return ariaLabel.trim();

        const ariaLabelledBy = t.getAttribute('aria-labelledby');
        if (ariaLabelledBy) {
            const firstId = ariaLabelledBy.split(/\s+/)[0];
            const labelledEl = document.getElementById(firstId);
            if (labelledEl) {
                const txt = (labelledEl.innerText || labelledEl.textContent || '').trim();
                if (txt) return txt;
            }
        }

        if (t.getAttribute('placeholder')) {
            return t.getAttribute('placeholder').trim();
        }

        if (t.id) {
            try {
                const safeId = t.id.replace(/(:|\.|\[|\]|,|=|@)/g, "\\$1");
                const label = document.querySelector('label[for="' + safeId + '"]');
                if (label) return (label.innerText || label.textContent).trim();
            } catch (e) {}
        }

        const parentLabel = t.closest('label');
        if (parentLabel) {
            const clone = parentLabel.cloneNode(true);
            const inputInside = clone.querySelector('input, select, textarea');
            if (inputInside) inputInside.remove();
            return (clone.innerText || clone.textContent).trim();
        }

        const title = t.getAttribute('title');
        if (title) return title.trim();

        const name = t.getAttribute('name');
        if (name) return name.trim();

        if (t.tagName.toLowerCase() === 'img') {
            return (t.getAttribute('alt') || '').trim();
        }

        if (t.tagName.toLowerCase() === 'input' && t.type === 'submit') {
            return t.value;
        }

        try {
            const clone = t.cloneNode(true);
            const badTags = clone.querySelectorAll('script, style, noscript, svg, path');
            badTags.forEach(bt => bt.remove());
            let rawText = (clone.textContent || '').replace(/\s+/g, ' ').trim();
            if (rawText.length > 60) return rawText.substring(0, 60) + '...';
            if (rawText.length > 0) return rawText;
        } catch (e) {}
        return '';
    }

    function generateSeleniumLocator(t, accName) {
        if (!t) return { type: 'By.cssSelector', value: '*' };
        var id = t.id;
        var name = t.name;
        var tag = t.tagName.toLowerCase();
        var normalizedAccName = (accName || '').replace(/'/g, "\\'");

        if (id) return { type: 'By.id', value: id };
        if (name) return { type: 'By.name', value: name };
        if (tag === 'a' && accName.length > 0) return { type: 'By.linkText', value: accName };
        if (accName.length > 0 && accName.length < 50) {
            return { type: 'By.xpath', value: "//*[normalize-space(text())='" + normalizedAccName + "']" };
        }

        let css = tag;
        if (t.className && typeof t.className === 'string') {
            const classes = t.className
                .trim()
                .split(/\s+/)
                .filter(c => !c.startsWith('ng-') && !c.includes('active'));
            if (classes.length > 0) css += '.' + classes.join('.');
        }
        return { type: 'By.cssSelector', value: css };
    }

    // =========================================================
    // 🛡️ NAVIGATION LOGIC – ONLY FIRST NAV GETS driver.get()
    // =========================================================
    function logNavigation(url) {
        if (!window.__recordedEvents) window.__recordedEvents = [];
        if (!window.__recorderNavState) window.__recorderNavState = { navCount: 0 };

        if (!url || typeof url !== 'string' || url.length === 0) return;
        const urlRegex = /^(http|https|file):\/\/[^\s$.?#].[^\s]*$/i;
        if (!urlRegex.test(url)) return;

        const escapedUrl = url.replace(/\"/g, '\\\"');
        const pageTitle = document.title && document.title.trim().length > 0
            ? document.title.trim()
            : '';
        let pageName = pageTitle || url;
        pageName = pageName.trim().replace(/\"/g, '\\"');

        window.__recorderNavState.navCount += 1;

        const alreadyHasGlobalGet = hasGlobalNavGet(url);
        const alreadyHasLocalGet = window.__recordedEvents.some(ev =>
            ev.type === 'navigation' &&
            typeof ev.raw_selenium === 'string' &&
            ev.raw_selenium.trim() !== ''
        );

        const isFirstNavWithGet = !alreadyHasGlobalGet && !alreadyHasLocalGet;

        // 🔍 Detect logout consent page
        const isLogoutConsent =
            /logoutConsent\.jsp/i.test(url) ||
            /single sign-off consent/i.test(pageTitle);

        // 🔍 Detect "we are coming FROM logout consent flow" (like after Confirm)
        let isFromLogoutConsentFlow = false;
        try {
            const evs = window.__recordedEvents;
            if (evs && evs.length > 0) {
                const lastEv = evs[evs.length - 1];
                const lastTitle = (lastEv.title || '');
                const lastUrl = (lastEv.url || '');
                const lastGherkin = (lastEv.raw_gherkin || '');
                const lastPrimary = (lastEv.options && lastEv.options.primary_name) || '';

                if (
                    /single sign-off consent/i.test(lastTitle) ||
                    /logoutConsent\.jsp/i.test(lastUrl) ||
                    (
                        /confirm/i.test(lastPrimary) &&
                        /single sign-off consent/i.test(pageTitle)
                    ) ||
                    /single sign-off consent/i.test(lastGherkin)
                ) {
                    isFromLogoutConsentFlow = true;
                }
            }
        } catch (e) {}

        let stepText = isFirstNavWithGet
            ? `I navigate to "${pageName}" page`
            : `I am on "${pageName}" page`;

        let rawSelenium = isFirstNavWithGet
            ? 'driver.get(\"' + escapedUrl + '\");'
            : '';

        // ⛔ Never emit driver.get for the logout consent page itself
        if (isLogoutConsent) {
            stepText = `I am on "${pageName}" page`;
            rawSelenium = '';
        }

        // ⛔ Also: if we just came FROM the logout consent flow and
        // are now going to obrareq (Sign In), do NOT emit driver.get
        if (isFromLogoutConsentFlow && /\/oam\/server\/obrareq\.cgi/i.test(url)) {
            stepText = `I am on "${pageName}" page`;
            rawSelenium = '';
        }

        const navRec = {
            timestamp: Date.now(),
            type: 'navigation',
            action: 'navigate',
            url: url,
            title: pageTitle,
            raw_gherkin: stepText,
            raw_selenium: rawSelenium
        };

        // =====================================================
        // SSO SPECIAL HANDLING (obrareq) – still intact
        // =====================================================
        let syntheticSsoClick = null;
        try {
            if (/\/oam\/server\/obrareq\.cgi/i.test(url)) {
                let cameFromLogout = false;
                if (window.__recordedEvents && window.__recordedEvents.length > 0) {
                    const lastEvent = window.__recordedEvents[window.__recordedEvents.length - 1];

                    if (lastEvent.url && /logout|sign-off|consent/i.test(lastEvent.url)) cameFromLogout = true;
                    if (lastEvent.title && /logout|sign-off|consent/i.test(lastEvent.title)) cameFromLogout = true;

                    if (lastEvent.type === 'click') {
                        const clickText = (lastEvent.raw_gherkin || '') +
                            ((lastEvent.options && lastEvent.options.primary_name) || '');
                        if (/sign out|sign off|logout|confirm/i.test(clickText)) {
                            cameFromLogout = true;
                        }
                    }
                }

                if (cameFromLogout) {
                    console.log('[recorder] SSO Auto-Click Blocked: Detected Logout');
                } else {
                    var now = Date.now();
                    if (!window.__lastSsoSyntheticClickTs ||
                        now - window.__lastSsoSyntheticClickTs > 3000) {

                        var ssoBtn = document.getElementById('ssoBtn');
                        if (ssoBtn && window.__recordingInstalled) {
                            var accName = getAccessibleName(ssoBtn);
                            var gherkinName = accName || 'Company Single Sign-On';
                            var locator = generateSeleniumLocator(ssoBtn, accName);
                            syntheticSsoClick = {
                                timestamp: now,
                                type: 'click',
                                title: document.title,
                                action: 'click',
                                selector: 'button',
                                raw_gherkin: 'I click on the "' + gherkinName + '" button',
                                raw_selenium: 'driver.findElement(' + locator.type + '("' +
                                    locator.value.replace(/\"/g, '\\\"') + '")).click();',
                                options: { primary_name: gherkinName }
                            };
                            window.__lastSsoSyntheticClickTs = now;
                        }
                    }
                }
            }
        } catch (e) { }

        // If this is the first nav that emits driver.get, mark globally
        if (isFirstNavWithGet && rawSelenium) {
            markGlobalNavGet();
        }

        persistEvent(navRec);
        window.__currentUrl = url;

        if (syntheticSsoClick) {
            persistEvent(syntheticSsoClick);
        }
    }

    window.__logNavigation = logNavigation;

    setTimeout(() => { logNavigation(window.location.href); }, 500);

    window.addEventListener('popstate', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) {
                logNavigation(window.location.href);
            }
        }, 100);
    });

    window.addEventListener('hashchange', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) {
                logNavigation(window.location.href);
            }
        }, 100);
    });

    (function (history) {
        const pushState = history.pushState;
        history.pushState = function () {
            const result = pushState.apply(history, arguments);
            setTimeout(() => {
                if (window.location.href !== window.__currentUrl) {
                    logNavigation(window.location.href);
                }
            }, 100);
            return result;
        };
    })(window.history);

    window.addEventListener('beforeunload', function () {
        try {
            if (window.__recordedEvents) {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents));
            }
        } catch (e) { }
    });

    // =========================================================
    // 🛠️ STANDARD RECORDING LOGIC
    // =========================================================
    function isInteractive(el) {
        if (!el || el.nodeType !== 1) return false;
        const tag = el.tagName.toLowerCase();

        // Oracle JET custom buttons
        if (tag === 'oj-button' || tag === 'oj-bind-button') return true;

        if (['a', 'button', 'input', 'select', 'textarea', 'details', 'summary'].includes(tag)) {
            return true;
        }

        const role = el.getAttribute('role');
        if (['button', 'link', 'checkbox', 'radio', 'switch', 'tab', 'combobox', 'option', 'menuitem'].includes(role)) {
            return true;
        }

        if (el.hasAttribute('onclick') || el.hasAttribute('tabindex')) return true;

        try {
            const style = window.getComputedStyle(el);
            if (style && style.cursor === 'pointer') return true;
        } catch (e) {}

        return false;
    }

    function handleDropdownOptionEvent(e) {
        try {
            if (!e || !e.target || !e.target.closest) return;
            const optionEl = e.target.closest(
                '.select2-results__option, li[role="option"], [role="option"], li[aria-selected]'
            );
            if (!optionEl) return;
            const optionText = (optionEl.innerText || optionEl.textContent || '').trim();
            if (!optionText) return;

            const rec = {
                timestamp: Date.now(),
                type: 'click',
                title: document.title,
                action: 'click',
                raw_selenium:
                    'driver.findElement(By.xpath("//*[contains(normalize-space(.), \'' +
                    optionText.replace(/'/g, "\\'") + '\')]")).click();',
                raw_gherkin: 'I select "' + optionText + '" from the dropdown',
                selector: 'option',
                options: { element_text: optionText, primary_name: optionText }
            };
            persistEvent(rec);
        } catch (err) { }
    }
    document.addEventListener('click', handleDropdownOptionEvent, true);

        const IGNORED_TAGS = [
            'path', 'rect', 'circle', 'polygon', 'ellipse', 'g',
            'defs', 'use', 'line', 'polyline'
        ];

        function recordEvent(e) {
            try {
                if (!e) return;

                const fromOjAction = !!e._fromOjAction;

                // Ignore untrusted *native* events, but allow our synthetic ojAction events
                if (!fromOjAction && e.isTrusted === false) return;

                let target = e.target;
                let interactableEl = null;
                let depth = 0;

                // 🔹 Strong Oracle JET button fallback
                if (target && target.closest) {
                    const ojBtn = target.closest('oj-button,oj-bind-button,button.oj-button-button');
                    if (ojBtn) {
                        interactableEl = ojBtn;
                    }
                }

                // 🔹 Depth-limited climb if still nothing
                if (!interactableEl) {
                    while (target && depth < 5 && target !== document.body) {
                        const tagName = target.tagName.toLowerCase();
                        if (IGNORED_TAGS.includes(tagName)) {
                            target = target.parentElement;
                            depth++;
                            continue;
                        }
                        if (isInteractive(target)) {
                            interactableEl = target;
                            break;
                        }
                        target = target.parentElement;
                        depth++;
                    }
                }

                // 🔹 Existing closest fallback
                if (!interactableEl && e.target && e.target.closest) {
                    interactableEl = e.target.closest(
                        'oj-button,oj-bind-button,button,[role="button"],a,input,select,textarea'
                    );
                }

                // SVG/icon fallback
                if (!interactableEl && e.target && IGNORED_TAGS.includes(e.target.tagName.toLowerCase())) {
                    interactableEl = e.target.closest('button, a, svg, [role="button"]');
                }

                if (!interactableEl) return;

                const t = interactableEl;
                const tag = t.tagName.toLowerCase();
                const type = t.type || '';

                // 🔴 KEY: For native clicks inside oj-button, SKIP.
                // We only want the ojAction-synthesized click.
                if (!fromOjAction && e.type === 'click') {
                    if (
                        tag === 'oj-button' ||
                        (t.closest && t.closest('oj-button'))
                    ) {
                        // console.log('[recorder] Skipping native click inside oj-button (ojAction will handle it)');
                        return;
                    }
                }

                const accName = getAccessibleName(t);
                const locator = generateSeleniumLocator(t, accName);
                const locatorValue = locator.value.replace(/\"/g, '\\\"');

                const rec = {
                    timestamp: Date.now(),
                    type: fromOjAction ? 'click' : e.type,
                    title: document.title,
                    selector: tag,
                    options: { id: t.id, name: t.name, primary_name: accName }
                };

                if (fromOjAction || e.type === 'click') {
                    if (tag === 'input' &&
                        !['checkbox', 'radio', 'button', 'submit', 'reset'].includes(type) &&
                        type !== 'image') {
                        return;
                    }
                    if (tag === 'textarea') return;

                    rec.action = 'click';
                    rec.raw_selenium = `driver.findElement(${locator.type}("${locatorValue}")).click();`;
                    rec.raw_gherkin = `I click on the "${accName || tag}" element`;

                    if (tag === 'a') {
                        rec.raw_gherkin = `I click on the "${accName}" link`;
                    } else if (type === 'submit' || type === 'button' || tag === 'button' || tag === 'oj-button') {
                        rec.raw_gherkin = `I click on the "${accName}" button`;
                    } else if (type === 'checkbox') {
                        rec.raw_gherkin = t.checked
                            ? `I check "${accName}"`
                            : `I uncheck "${accName}"`;
                    }

                    persistEvent(rec);

                } else if (e.type === 'change') {
                    const value = t.value;
                    rec.action = 'sendKeys';
                    rec.options.value = value;
                    rec.raw_selenium =
                        `driver.findElement(${locator.type}("${locatorValue}")).sendKeys("${value}");`;
                    rec.raw_gherkin = `I enter "${value}" into "${accName}"`;

                    if (tag === 'select') {
                        rec.action = 'select';
                        rec.raw_gherkin = `I select "${value}" from "${accName}"`;
                        persistEvent(rec);
                    } else {
                        const fieldKey = (t.id || t.name || locator.value);
                        if (pendingTextChangeTimers[fieldKey]) {
                            clearTimeout(pendingTextChangeTimers[fieldKey].timeoutId);
                        }
                        pendingTextChangeTimers[fieldKey] = {
                            rec: rec,
                            timeoutId: setTimeout(() => {
                                persistEvent(rec);
                                delete pendingTextChangeTimers[fieldKey];
                            }, TEXT_CHANGE_DEBOUNCE_MS)
                        };
                    }
                }
            } catch (err) {
                console.log('[recorder] Error:', err);
            }
        }

        // Native listeners
        ['click', 'change'].forEach(type =>
            window.addEventListener(type, recordEvent, true)
        );

        // 🔹 Oracle JET ojAction → treat as a single canonical click
        window.addEventListener('ojAction', function (e) {
            try {
                let btn = e.target;
                if (btn && btn.closest) {
                    const ojBtn = btn.closest('oj-button,oj-bind-button,button.oj-button-button');
                    if (ojBtn) btn = ojBtn;
                }

                recordEvent({
                    type: 'click',
                    target: btn,
                    isTrusted: true,     // allow it
                    _fromOjAction: true  // custom flag so we don't skip it
                });
            } catch (err) {
                console.log('[recorder] ojAction mapping error:', err);
            }
        }, true);

})();