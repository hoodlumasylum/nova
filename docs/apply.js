(function () {
  var form = document.getElementById("apply-form");
  if (!form) return;
  var org = document.getElementById("org-fields");
  var orgName = document.getElementById("org-name");
  var orgSize = document.getElementById("org-size");
  var ORG = "Schools, colleges, universities and other organisations";

  function selectedRole() {
    var picked = form.querySelector('input[name="role"]:checked');
    return picked ? picked.value : "";
  }

  function syncOrg() {
    var show = selectedRole() === ORG;
    org.hidden = !show;
    orgName.required = show;
    orgSize.required = show;
    if (!show) {
      orgName.value = "";
      orgSize.value = "";
    }
  }

  form.querySelectorAll('input[name="role"]').forEach(function (r) {
    r.addEventListener("change", syncOrg);
  });
  syncOrg();
})();
