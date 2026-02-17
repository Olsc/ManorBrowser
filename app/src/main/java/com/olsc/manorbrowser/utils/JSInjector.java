package com.olsc.manorbrowser.utils;

public class JSInjector {

    // 改进的登录检测脚本，支持更多表单格式
    public static final String INJECT_LOGIN_DETECT =
        "(function() {" +
        "    if (window.manorInjected) return;" +
        "    window.manorInjected = true;" +
        "    " +
        "    function detectAndSave() {" +
        "        var forms = document.querySelectorAll('form');" +
        "        forms.forEach(function(form) {" +
        "            form.addEventListener('submit', function(e) {" +
        "                var passInputs = form.querySelectorAll('input[type=\"password\"]');" +
        "                if (passInputs.length === 0) return;" +
        "                " +
        "                var passInput = passInputs[0];" +
        "                if (!passInput.value) return;" +
        "                " +
        "                var username = '';" +
        "                var inputs = Array.from(form.querySelectorAll('input'));" +
        "                " +
        "                for (var i = 0; i < inputs.length; i++) {" +
        "                    var inp = inputs[i];" +
        "                    var type = inp.type.toLowerCase();" +
        "                    var name = (inp.name || '').toLowerCase();" +
        "                    var id = (inp.id || '').toLowerCase();" +
        "                    " +
        "                    if (inp === passInput) break;" +
        "                    " +
        "                    if (type === 'text' || type === 'email' || type === 'tel' || " +
        "                        name.includes('user') || name.includes('email') || name.includes('login') || name.includes('account') ||" +
        "                        id.includes('user') || id.includes('email') || id.includes('login') || id.includes('account')) {" +
        "                        if (inp.value) username = inp.value;" +
        "                    }" +
        "                }" +
        "                " +
        "                if (username && passInput.value) {" +
        "                    prompt('MANOR_SAVE_PASS|' + window.location.href + '|' + username + '|' + passInput.value);" +
        "                }" +
        "            }, true);" +
        "        });" +
        "    }" +
        "    " +
        "    detectAndSave();" +
        "    " +
        "    setTimeout(detectAndSave, 1000);" +
        "    setTimeout(detectAndSave, 3000);" +
        "})();";

    // 改进的自动填充脚本，支持更多输入框格式
    public static String getFillScript(String username, String password) {
        String escapedUser = escape(username);
        String escapedPass = escape(password);
        
        return "(function() {" +
               "    console.log('Manor: Starting autofill');" +
               "    " +
               "    var passInputs = document.querySelectorAll('input[type=\"password\"]');" +
               "    if (passInputs.length === 0) {" +
               "        passInputs = document.querySelectorAll('input[name*=\"pass\"], input[id*=\"pass\"]');" +
               "    }" +
               "    " +
               "    if (passInputs.length > 0) {" +
               "        var passInput = passInputs[0];" +
               "        passInput.value = \"" + escapedPass + "\";" +
               "        " +
               "        var form = passInput.form || passInput.closest('form');" +
               "        if (!form) {" +
               "            form = passInput.parentElement;" +
               "            while (form && form.tagName !== 'FORM' && form !== document.body) {" +
               "                form = form.parentElement;" +
               "            }" +
               "        }" +
               "        " +
               "        if (form) {" +
               "            var userInput = null;" +
               "            var inputs = Array.from(form.querySelectorAll('input'));" +
               "            " +
               "            for (var i = 0; i < inputs.length; i++) {" +
               "                var inp = inputs[i];" +
               "                if (inp === passInput) break;" +
               "                " +
               "                var type = inp.type.toLowerCase();" +
               "                var name = (inp.name || '').toLowerCase();" +
               "                var id = (inp.id || '').toLowerCase();" +
               "                var placeholder = (inp.placeholder || '').toLowerCase();" +
               "                " +
               "                if (type === 'text' || type === 'email' || type === 'tel' || " +
               "                    name.includes('user') || name.includes('email') || name.includes('login') || name.includes('account') ||" +
               "                    id.includes('user') || id.includes('email') || id.includes('login') || id.includes('account') ||" +
               "                    placeholder.includes('user') || placeholder.includes('email') || placeholder.includes('login')) {" +
               "                    userInput = inp;" +
               "                }" +
               "            }" +
               "            " +
               "            if (userInput) {" +
               "                userInput.value = \"" + escapedUser + "\";" +
               "                userInput.dispatchEvent(new Event('input', { bubbles: true }));" +
               "                userInput.dispatchEvent(new Event('change', { bubbles: true }));" +
               "                console.log('Manor: Username filled');" +
               "            }" +
               "        }" +
               "        " +
               "        passInput.dispatchEvent(new Event('input', { bubbles: true }));" +
               "        passInput.dispatchEvent(new Event('change', { bubbles: true }));" +
               "        console.log('Manor: Password filled');" +
               "    } else {" +
               "        console.log('Manor: No password input found');" +
               "    }" +
               "})();";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
