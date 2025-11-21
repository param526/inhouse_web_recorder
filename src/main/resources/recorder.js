(function() {

    // Exit if the script has already been installed
    if (window.__recordingInstalled) return;

    window.__recordingInstalled = true;
    window.__recordedEvents = window.__recordedEvents || [];
    window.__currentUrl = window.location.href;

    /**
     * Logs a navigation event and updates the current URL tracker.
     */
    function logNavigation(url) {

    // --- ⭐️ URL Validation Logic ⭐️ ---
        if (!url || typeof url !== 'string' || url.length === 0) {
            console.warn('Skipping navigation event: URL is empty or invalid.');
            return; // Stop recording if URL is invalid
        }

        // 1. Check for well-formed URL structure (e.g., must contain a protocol)
        // This simple regex checks for http(s):// or file:/// followed by non-space characters
        const urlRegex = /^(http|https|file):\/\/[^\s$.?#].[^\s]*$/i;

        if (!urlRegex.test(url)) {
            console.warn('Skipping navigation event: URL does not look like an absolute URL (missing protocol).', url);
            return; // Stop recording if validation fails
        }
        // --- ⭐️ End Validation Logic ⭐️ ---

        var escapedUrl = url.replace(/\"/g, '\\\"');
        var rec = {
            timestamp: Date.now(),
            type: 'navigation',
            action: 'navigate',
            url: url,
            title: document.title,
            // 🛠️ CORRECTED: Semicolon removed
            raw_gherkin: 'I visit "' + document.title + '" page',
            raw_selenium: 'driver.get(\"' + escapedUrl + '\");'
        };
        (window.__recordedEvents || (window.__recordedEvents = [])).push(rec);
        window.__currentUrl = url;
    }

    // --- URL Change Detection (for initial load and SPAs) ---
    logNavigation(window.__currentUrl);

    // Listeners for URL changes with a small delay to allow title to update
    window.addEventListener('popstate', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) logNavigation(window.location.href);
        }, 100);
    });
    window.addEventListener('hashchange', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) logNavigation(window.location.href);
        }, 100);
    });

    // Wrap pushState to catch manual URL updates in SPAs
    (function(history) {
        var pushState = history.pushState;
        history.pushState = function() {
            var result = pushState.apply(history, arguments);
            setTimeout(() => {
                if (window.location.href !== window.__currentUrl) logNavigation(window.location.href);
            }, 100);
            return result;
        };
    })(window.history);
    // --- End URL Change Detection ---


    /**
     * Gets the most specific ACCESSIBLE NAME (aria-label, placeholder, associated <label>).
     */
    function getAccessibleName(t) {
        var label = t.getAttribute('aria-label') || t.placeholder;
        if (label) return label.trim();
        var id = t.id;
        if (id) {
            var element = document.querySelector('label[for="' + id + '\"]');
            if (element) return (element.innerText || element.textContent).trim();
        }
        if (t.name) return t.name.trim();

        return '';
    }

    /**
     * Generates the most robust Selenium locator ({type, value}),
     * following the Gherkin readability priority: Text > Name > ID.
     */
    function generateSeleniumLocator(t, accName) {
        var id = t.id;
        var name = t.name;
        var tag = t.tagName.toLowerCase();

        // 1. Get Element Text
        var elementText = t.innerText ? t.innerText.trim() : (t.textContent ? t.textContent.trim() : '');
        var normalizedText = elementText.replace(/'/g, "\\'");

        // 2. Get Accessible Name Text
        var normalizedAccName = accName.replace(/'/g, "\\'");

        // ⭐️ PRIORITY 1: Element Text (XPath by Text) ⭐️
        // Exclude standard input types, as their text value changes
        if (elementText.length > 0 && tag !== 'input' && tag !== 'textarea') {
            var xpath = "//" + tag + "[normalize-space(.)='" + normalizedText + "']";
            return { type: 'By.xpath', value: xpath };
        }

        // ⭐️ PRIORITY 2: By Name ⭐️
        if (name && name.length > 0) {
            return { type: 'By.name', value: name };
        }

        // ⭐️ PRIORITY 3: By ID ⭐️
        if (id && id.length > 0) {
            return { type: 'By.id', value: id };
        }

        // PRIORITY 4: Accessible XPath
        if (accName.length > 0) {
            var xpath = "//label[normalize-space(.)='" + normalizedAccName + "']/following-sibling::" + tag +
                " | //" + tag + "[@aria-label='" + normalizedAccName + "']" +
                " | //" + tag + "[@placeholder='" + normalizedAccName + "']";
            return { type: 'By.xpath', value: xpath };
        }

        // Final Fallback: CSS Selector
        return { type: 'By.cssSelector', value: tag };
    }

    /**
     * Main event recorder function for click and change events.
     */
    function recordEvent(e) {
        try {
            var t = e.target;
            var rec = {};
            rec.timestamp = Date.now();
            rec.type = e.type;
            rec.title = document.title;

            var accName = getAccessibleName(t);
            var elementText = t.innerText ? t.innerText.trim() : (t.textContent ? t.textContent.trim() : '');

            // --- Role Determination Logic ---
            var role = t.getAttribute('role');

            if (!role && t.tagName.toLowerCase() === 'input') {
                if (t.type === 'checkbox') {
                    role = 'checkbox';
                } else if (t.type === 'radio') {
                    role = 'radio';
                } else if (t.type === 'text' || t.type === 'password' || t.type === 'email' || t.type === 'search' || t.type === 'number') {
                    role = 'textbox';
                } else {
                    role = t.type;
                }
            } else if (!role && t.tagName.toLowerCase() === 'textarea') {
                role = 'textbox';
            } else if (!role) {
                role = t.tagName.toLowerCase();
            }

            // --- Gherkin Role Suffix Logic (used for fallback labeling only) ---
            var gherkinRole = role;
            if (role === 'radio') {
                gherkinRole = 'radio button';
            } else if (role === 'checkbox') {
                gherkinRole = 'checkbox button';
            } else if (role === 'textbox') {
                gherkinRole = 'textbox field';
            } else if (role === 'button') {
                gherkinRole = 'button';
            } else {
                gherkinRole = role;
            }
            // --- End Role Logic ---

            var locator = generateSeleniumLocator(t, accName);
            var locatorValue = locator.value.replace(/\"/g, '\\\"');

            rec.selector = role;

            // ⭐️ GHERKIN NAMING PRIORITY: element_text > name > accName > id ⭐️
            var gherkinName = '';

            if (elementText.length > 0) {
                 gherkinName = elementText;
            } else if (t.name && t.name.length > 0) {
                 gherkinName = t.name;
            } else if (accName.length > 0) {
                 gherkinName = accName;
            } else if (t.id && t.id.length > 0) {
                 gherkinName = t.id;
            }
            // --- End Gherkin Naming Priority ---


            // ⭐️ UNIFIED OPTIONS ASSEMBLY (Applies to ALL actions) ⭐️
            rec.options = {};

            if (elementText.length > 0) {
                 rec.options.element_text = elementText;
            }

            if (t.id) rec.options.id = t.id;
            if (t.name) rec.options.name = t.name;

            rec.options.primary_name = gherkinName;
            // ----------------------------------------------------

            switch (e.type) {
                case 'click':
                    rec.action = 'click';
                    rec.raw_selenium = 'driver.findElement(' + locator.type + '(\"' + locatorValue + '\")).click();';
                    // REMOVED gherkinRole
                    rec.raw_gherkin = 'I click on the "' + gherkinName + '"';
                    break;

                case 'change':
                    var value = t.value !== undefined ? t.value : (t.type === 'checkbox' || t.type === 'radio' ? (t.checked ? 'checked' : 'unchecked') : null);

                    rec.action = 'sendKeys';
                    rec.parsedValue = value;
                    rec.value = value;
                    rec.raw_selenium = 'driver.findElement(' + locator.type + '(\"' + locatorValue + '\")).sendKeys(\"' + value + '\");';
                    // REMOVED gherkinRole
                    rec.raw_gherkin = 'I enter "' + value + '" into the "' + gherkinName + '"';

                    // Add change-specific fields to options
                    rec.options.value = value;
                    rec.options.parsedValue = value;
                    break;

                default:
                    return;
            }

            if (!gherkinName || gherkinName.length === 0) {
                rec.raw_gherkin = 'I interact with the unlabeled ' + gherkinRole + ' element'; // Fallback keeps the role for context
            }

            (window.__recordedEvents || (window.__recordedEvents = [])).push(rec);
        } catch (e) {
            console && console.log && console.log('recordEvent error', e);
        }
    }

    // Attach listeners to document for capturing interaction events
    ['click', 'change'].forEach(function(type) {
        document.addEventListener(type, recordEvent, true);
    });

})();