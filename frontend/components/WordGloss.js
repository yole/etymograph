export default function WordGloss(params) {
    let gloss = params.gloss
    if (gloss === null) return <></>

    let glossCategory = null

    let categoryStart = gloss.indexOf('.')
    if (categoryStart > 0 && gloss.substring(categoryStart) === gloss.substring(categoryStart).toUpperCase()) {
        const dash = gloss.indexOf('-')
        if (dash < categoryStart && gloss.substring(dash) === gloss.substring(dash).toUpperCase()) {
            categoryStart = dash
        }

        glossCategory = gloss.substring(categoryStart).toLowerCase()
        gloss =  gloss.substring(0, categoryStart)
    }

    return <>
        {gloss}{glossCategory && <span className="glossAbbreviation">{glossCategory}</span>}
    </>

}
export function WordFullGloss(params) {
    const word = params.word
    const shortGlossIndex = word.fullGloss.toLowerCase().indexOf(word.gloss.toLowerCase())
    if (shortGlossIndex >= 0) {
        return <>
            {word.fullGloss.substring(0, shortGlossIndex)}
            <b>{word.gloss}</b>
            {word.fullGloss.substring(shortGlossIndex + word.gloss.length)}
        </>
    }
    return word.fullGloss
}
