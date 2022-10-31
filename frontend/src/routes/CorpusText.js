import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch(process.env.REACT_APP_BACKEND_URL + "corpus/text/" + params.id, { headers: { 'Accept': 'application/json'} })
}

export default function CorpusText() {
    const corpusText = useLoaderData()
    return <>
        <h2>{corpusText.title}</h2>
        {corpusText.lines.map(l => (
            <div>
                <table><tbody>
                    <tr>
                        {l.words.map(w => <td><Link to={`/word/${corpusText.language}/${w.wordText}`}>{w.text}</Link></td>)}
                    </tr>
                    <tr>
                        {l.words.map(w => <td>{w.gloss}</td>)}
                    </tr>
                </tbody></table>
            </div>
        ))}
    </>
}
