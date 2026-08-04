package com.example.garminbridge

object GarminJsHook {

    val SCRIPT = """
        (function() {
            if (window.__garminHook) return;
            window.__garminHook = true;

            var PATTERNS = [
                'usersummary-service/usersummary/daily',
                'usersummary-service/stats/steps',
                'wellness-service/wellness/dailySummaryChart',
                'wellness-service/wellness/dailySleepData',
                'wellness-service/wellness/dailyHeartRate',
                'wellness-service/wellness/sleepDetails'
            ];

            function isInteresting(url) {
                if (!url) return false;
                for (var i = 0; i < PATTERNS.length; i++) {
                    if (url.indexOf(PATTERNS[i]) !== -1) return true;
                }
                return false;
            }

            function forward(url, text) {
                try {
                    if (window.GarminBridge) {
                        window.GarminBridge.onGarminData(url, text);
                    }
                } catch (e) {}
            }

            var origFetch = window.fetch;
            window.fetch = function(input, init) {
                var url = (typeof input === 'string') ? input : (input && input.url);
                return origFetch.apply(this, arguments).then(function(response) {
                    if (isInteresting(url)) {
                        response.clone().text().then(function(t) {
                            forward(url, t);
                        }).catch(function() {});
                    }
                    return response;
                });
            };

            var origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                this.__garminUrl = url;
                this.addEventListener('load', function() {
                    if (isInteresting(this.__garminUrl)) {
                        forward(this.__garminUrl, this.responseText);
                    }
                });
                return origOpen.apply(this, arguments);
            };
        })();
    """.trimIndent()
}