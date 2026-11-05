#ifdef LONG_CONTEXT
#define CONTEXT_TYPE long
#else
#define CONTEXT_TYPE int
#endif
/**
 * @brief A physical comment with two include contexts.
 * \param value The context-specific value.
 */
int contextual(CONTEXT_TYPE value);
#undef CONTEXT_TYPE
