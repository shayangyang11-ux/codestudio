-- A9 hard cut: legacy DOMAIN and SUBMODULE are no longer first-class visible nodes.
-- They are normalized to MODULE so new Requirement Agent UI has one clean visible module concept.
UPDATE project_module
SET module_type = 'MODULE',
    updated_at = NOW()
WHERE module_type IN ('DOMAIN', 'SUBMODULE');
