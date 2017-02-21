Thinking Identity Access Management (IAM)
=====================================================

* Whenever you create IAM users, give an email as the username. 

For example AWS supports it and you'll save a lot of time in trying to figure out who's who in your organization. Whenever you want to check if this issue should have certain permissions, you know how to contact the person and find out. 

* Prefer attaching policies over composing long ones 

If you start composing a lot of inline policies, soon you'll start merging them and soon you'll start hitting length limits which aren't that obviously documented. Instead, create new policies and attach them. 