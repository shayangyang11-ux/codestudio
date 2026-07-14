-- 将旧版本中保存的 Windows/Linux 绝对路径，
-- 转换为相对于附件存储根目录的 storage key。
--
-- 旧：
-- E:\CodeMind\codemind-studio\codemind-server\data\
-- assistant-attachments\<workspace>\<project>\<date>\<file>
--
-- 新：
-- <workspace>/<project>/<date>/<file>

UPDATE assistant_attachment
SET storage_path = regexp_replace(
        replace(storage_path, chr(92), '/'),
        '^.*?/assistant-attachments/',
        ''
                   ),
    metadata = COALESCE(
                       metadata,
                       '{}'::jsonb
               ) || jsonb_build_object(
                       'storageVersion',
                       'LOCAL_FILE_V2_RELATIVE_KEY'
                    )
WHERE storage_path IS NOT NULL
  AND storage_path <> ''
  AND replace(
        storage_path,
        chr(92),
        '/'
      ) ~ '(^|/)assistant-attachments/';

COMMENT ON COLUMN assistant_attachment.storage_path IS
    'Storage key relative to codemind.assistant.attachments.storage-root. Legacy absolute paths are normalized by Flyway.';