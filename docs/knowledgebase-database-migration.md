# Knowledge-base database migration contract

The default application profile does not initialize database contents. The H2-specific sample data lives under `demo/data.sql` and is loaded only by the explicit `demo` Spring profile. It must not be copied into a production migration.

## Target-database decisions

Create the Flyway or Liquibase migration in the host project after these deployment-specific choices are known:

1. Select the target database and its supported Markdown type (`TEXT` or CLOB).
2. Align the ID/sequence strategy with the host project's entity strategy. The PoC currently uses the shared `idgenerator` sequence with allocation size 50.
3. Map `created_by_id` and `updated_by_id` to the host project's user table, or remove those foreign keys if identity is supplied differently.
4. Decide whether `created_at`, `updated_at`, `created_by_id`, and `updated_by_id` remain entity-managed or use the host project's Spring Data auditing.
5. Confirm constraint naming and schema ownership conventions.

Do not enable `spring.jpa.hibernate.ddl-auto=validate` until the target migration creates the schema.

## Required schema

Create `kb_category` before `kb_article`.

`kb_category` requires:

| Column | Constraint |
| --- | --- |
| `id` | Primary key using the selected host ID strategy |
| `version` | Non-null integer for optimistic locking |
| `name` | Non-null string |
| `slug` | Non-null string with a unique constraint |
| `description` | Nullable string, maximum 500 characters |
| `parent_id` | Nullable self-referencing foreign key |
| `sort_order` | Non-null integer |

Add an index supporting hierarchy traversal and ordering by `parent_id`, `sort_order`, and `id`.

`kb_article` requires:

| Column | Constraint |
| --- | --- |
| `id` | Primary key using the selected host ID strategy |
| `version` | Non-null integer for optimistic locking |
| `title` | Non-null string |
| `slug` | Non-null string with a unique constraint |
| `content` | Nullable target-specific `TEXT` or CLOB, compatible with the 20,000-character application limit |
| `created_by_id` | Nullable host-user foreign key when applicable |
| `updated_by_id` | Nullable host-user foreign key when applicable |
| `created_at` | Non-null timestamp |
| `updated_at` | Non-null timestamp |
| `status` | Non-null string restricted to `DRAFT` and `PUBLISHED` |
| `category_id` | Nullable foreign key to `kb_category` |
| `sort_order` | Non-null integer |

Add an index supporting category ordering by `category_id`, `sort_order`, and `id`. Do not add cascading deletes: category deletion is guarded by the service and articles must not disappear as a hidden consequence.

## Migration verification

Run the migration against the actual target database, then start the application with Hibernate schema validation enabled. Verify creation, update, hierarchy queries, slug uniqueness, optimistic locking, Markdown content at the size limit, and user audit references. Keep demo users, profile images, and sample articles out of this verification migration.
