#ifndef DOCUMENTED_API_H
#define DOCUMENTED_API_H
/**
 * @brief Header process brief.
 * @param count Input count text; the word count stays here.
 * \return The count value is unchanged.
 */
int process(int count);
/**
 * \brief Identity template brief.
 * @tparam T The value type.
 * \tparam U Another named type, preserved in order.
 * \param value The unchanged value.
 * @return The same value.
 * @custom Keep this unknown command and count text exactly.
 */
template<class T, typename U> T identity(T value) { return value; }
/** @param size An unmatched name remains documented. */
int mismatch(int count);
/// @brief A line documentation brief.
/// Continued brief text.
/// \param sample First parameter description.
/// Continued parameter text.
/// @return A documented result.
/// @extension Keep an unknown line command.
int lineDocs(int sample);
#endif
