import {useState} from "react";
import {addLanguage} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";

export default function NewLanguage() {
    const [name, setName] = useState("")
    const [shortName, setShortName] = useState("")
    const [reconstructed, setReconstructed] = useState(false)
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph

    function saveLanguage() {
        addLanguage(graph, name, shortName, reconstructed)
            .then(r => {
                if (r.status === 200) {
                    r.json().then(r => router.push(`/${graph}/language/${r.shortName}`))
                }
                else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save language"))
                }
            })
    }

    return <>
        <Breadcrumbs title="New Language"/>

        <table><tbody>
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Short name:</label></td>
            <td><input type="text" value={shortName} onChange={(e) => setShortName(e.target.value)}/></td>
        </tr>
        </tbody></table>

        <label>
            <input type="checkbox" checked={reconstructed} onChange={(e) => setReconstructed(!reconstructed)}/>
            Reconstructed
        </label>

        {errorText !== "" && <div className="errorText">{errorText}</div>}
        <p>
        <button onClick={saveLanguage}>Save</button>
        </p>
    </>
}
