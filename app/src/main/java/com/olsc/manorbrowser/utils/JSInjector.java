package com.olsc.manorbrowser.utils;

public class JSInjector {

    public static final String INJECT_LOGIN_DETECT = 
        "(function() {" +
        "    if (window.manorInjected) return;" +
        "    window.manorInjected = true;" +
        "    document.addEventListener('submit', function(e) {" +
        "        var form = e.target;" +
        "        var passInputs = form.querySelectorAll('input[type=\"password\"]');" +
        "        if (passInputs.length > 0) {" +
        "            var passInput = passInputs[0];" +
        "            if (!passInput.value) return;" +
        "            var inputs = Array.from(form.querySelectorAll('input'));" +
        "            var passIdx = inputs.indexOf(passInput);" +
        "            var username = '';" +
        "            for (var i = passIdx - 1; i >= 0; i--) {" +
        "                var type = inputs[i].type;" +
        "                if (type === 'text' || type === 'email') {" +
        "                     username = inputs[i].value;" +
        "                     break;" +
        "                }" +
        "            }" +
        "            if (username && passInput.value) {" +
        "                prompt('MANOR_SAVE_PASS|' + window.location.href + '|' + username + '|' + passInput.value);" +
        "            }" +
        "        }" +
        "    }, true);" +
        "})();";

    public static String getFillScript(String username, String password) {
        return "(function() {" +
               "    var passes = document.querySelectorAll('input[type=\"password\"]');" +
               "    console.log('Manor: Filling password for " + username + "');" +
               "    if (passes.length > 0) {" +
               "        var p = passes[0];" +
               "        p.value = \"" + escape(password) + "\";" +
               "        var form = p.form;" +
               "        if (form) {" +
               "             var inputs = Array.from(form.querySelectorAll('input'));" +
               "             var idx = inputs.indexOf(p);" +
               "             for (var i = idx - 1; i >= 0; i--) {" +
               "                var type = inputs[i].type;" +
               "                if (type === 'text' || type === 'email') {" +
               "                     inputs[i].value = \"" + escape(username) + "\";" +
               "                     var event = new Event('input', { bubbles: true });" +
               "                     inputs[i].dispatchEvent(event);" +
               "                     break;" +
               "                }" +
               "            }" +
               "        }" +
               "        var event = new Event('input', { bubbles: true });" +
               "        p.dispatchEvent(event);" +
               "    }" +
               "})();";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
