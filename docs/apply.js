(function () {
  var form = document.getElementById("apply-form");
  if (!form) return;

  var org = document.getElementById("org-fields");
  var orgName = document.getElementById("org-name");
  var orgSize = document.getElementById("org-size");
  var ageBox = document.getElementById("age-fields");
  var age = document.getElementById("child-age");
  var ageLabel = document.getElementById("age-label");
  var parentBox = document.getElementById("parent-fields");
  var courseBox = document.getElementById("course-note");
  var courseAgree = document.getElementById("course-agree");
  var ideasLabel = document.getElementById("ideas-label");
  var ideasHelp = document.getElementById("ideas-help");
  var ideaListHelp = document.getElementById("idea-list-help");
  var teachInputs = form.querySelectorAll(".teach-list input");

  var ORG = "Schools, colleges, universities and other organisations";
  var IDEAS_DEFAULT = "What ideas or inventions do you have?";
  var IDEAS_PARENT = "What ideas does your child have?";

  function selectedRole() {
    var picked = form.querySelector('input[name="role"]:checked');
    return picked ? picked.value : "";
  }

  function setRequired(el, on) {
    if (!el) return;
    el.required = !!on;
    if (!on && "value" in el) {
      if (el.type === "checkbox") el.checked = false;
      else el.value = "";
    }
  }

  function sync() {
    var role = selectedRole();
    var isOrg = role === ORG;
    var isParent = role === "Parent";
    var isStudent = role === "Student";
    var isTeacher = role === "Teacher";
    var needsAge = isParent || isStudent;

    org.hidden = !isOrg;
    setRequired(orgName, isOrg);
    setRequired(orgSize, isOrg);

    ageBox.hidden = !needsAge;
    setRequired(age, needsAge);
    if (ageLabel) {
      ageLabel.textContent = isStudent ? "Your age" : "Child’s age";
    }

    parentBox.hidden = !isParent;
    for (var i = 0; i < teachInputs.length; i++) {
      setRequired(teachInputs[i], isParent);
    }
    ideasHelp.hidden = !isParent;
    if (ideaListHelp) ideaListHelp.hidden = !isParent;
    ideasLabel.textContent = isParent ? IDEAS_PARENT : IDEAS_DEFAULT;

    courseBox.hidden = !isTeacher;
    setRequired(courseAgree, isTeacher);
  }

  form.querySelectorAll('input[name="role"]').forEach(function (r) {
    r.addEventListener("change", sync);
  });
  sync();
})();
