# BioPulse working agreement

Use Java 21, Spring Boot, explicit JDBC and MySQL. Do not introduce ORM persistence.
Preserve provenance, recorded timestamps, units and missing values. Label generated fixtures and research outputs.
Use the visual contract in design/framer/HANDOFF.md for the React frontend.
Before completing a task, run relevant tests, inspect git diff for secrets and unintended changes, commit the completed work and push the current branch to origin. The user authorizes routine commits and pushes for this project; never force push or commit secrets. Report verification failures honestly. Run Maven verify (Docker required) and frontend build for full-stack changes.
