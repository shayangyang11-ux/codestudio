-- Phase 10 A8.4 final asset model
-- Adds the formal PAGE_MAP requirement asset type and migrates legacy MODULE-level PAGE_SPEC rows.
-- This migration is defensive: it checks table/column existence before touching data.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'artifact_version'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'project_module'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'artifact_version' AND column_name = 'artifact_type'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'artifact_version' AND column_name = 'module_id'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'project_module' AND column_name = 'id'
    ) THEN
        UPDATE artifact_version av
        SET artifact_type = 'PAGE_MAP'
        FROM project_module pm
        WHERE av.module_id = pm.id
          AND av.artifact_type = 'PAGE_SPEC'
          AND upper(coalesce(pm.module_type, '')) IN (
              'MODULE', 'BUSINESS_MODULE', 'FEATURE_MODULE', 'INTEGRATION_MODULE', 'INFRA_MODULE'
          );
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'requirement_artifact'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'project_module'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'requirement_artifact' AND column_name = 'artifact_type'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'requirement_artifact' AND column_name = 'module_id'
    ) THEN
        UPDATE requirement_artifact ra
        SET artifact_type = 'PAGE_MAP'
        FROM project_module pm
        WHERE ra.module_id = pm.id
          AND ra.artifact_type = 'PAGE_SPEC'
          AND upper(coalesce(pm.module_type, '')) IN (
              'MODULE', 'BUSINESS_MODULE', 'FEATURE_MODULE', 'INTEGRATION_MODULE', 'INFRA_MODULE'
          );
    END IF;
END $$;
-- A8.4.3: prompt_template seed intentionally skipped.
-- The local prompt_template schema does not consistently contain template_content.
-- Requirement Agent prompts are loaded from codemind-agent-service/app/prompts/requirement/*.md.
