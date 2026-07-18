plugins {
    id("dev.kikugie.stonecutter")
}
// Keep the migration bootstrap focused on the already proven baseline. The
// full matrix is re-enabled only after this node compiles for every loader.
stonecutter active "26.1.2"
