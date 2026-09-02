import {WordViewModel} from "@/models";

export default function WordGloss(params: { gloss: string }) {
    let gloss = params.gloss
    if (gloss === null || gloss === "") return <>&lt;?&gt;</>

    function extractCategory(chunk: string): string[] {
        let categoryStart = chunk.indexOf('.')
        if (categoryStart > 0 && chunk.substring(categoryStart) === chunk.substring(categoryStart).toUpperCase()) {
            const dash = chunk.indexOf('-')
            if (dash < categoryStart && chunk.substring(dash) === chunk.substring(dash).toUpperCase()) {
                categoryStart = dash
            }

            return [chunk.substring(0, categoryStart), chunk.substring(categoryStart).toLowerCase()]
        }
        return [chunk, ""]
    }

    let cliticChain = gloss.split('=')
    return <>
        {cliticChain.map((chunk, index) => {
            const [baseGloss, glossCategory] = extractCategory(chunk)
            return <>{index > 0 && "="}{baseGloss}{glossCategory && <span className="glossAbbreviation">{glossCategory}</span>}</>
        })}
    </>
}
export function WordFullGloss(params: { word: WordViewModel }) {
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
