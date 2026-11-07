// C++03 20.2 utility synopsis and 20.2.1 [lib.operators]. MIT License.
// Independent expected interface, not derived from StandardLibraryCatalogue.
// Paragraph coordinates are retained from the source-reviewed ISO 2003 ledger.
namespace std { namespace rel_ops {
// 20.2.1/2-3: EqualityComparable prerequisite; result negates equality.
template<class T> bool operator!=(const T& x, const T& y);
// 20.2.1/4-5: LessThanComparable prerequisite; reverse strict comparison.
template<class T> bool operator>(const T& x, const T& y);
// 20.2.1/6-7: LessThanComparable prerequisite; negate reverse comparison.
template<class T> bool operator<=(const T& x, const T& y);
// 20.2.1/8-9: LessThanComparable prerequisite; negate forward comparison.
template<class T> bool operator>=(const T& x, const T& y);
} }
