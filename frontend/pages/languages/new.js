import {useState} from "react";
import {addLanguage} from "@/api";
import {useRouter} from "next/router";

export default function NewLanguage() {
    const [name, setName] = useState("")
    const [shortName, setShortName] = useState("")
    const [errorText, setErrorText] = useState("")
    const router = useRouter()

    function saveLanguage() {
        addLanguage(name, shortName)
            .then(r => {
                if (r.status === 200) {
                    r.json().then(r => router.push("/language/" + r.shortName))
                }
                else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save language"))
                }
            })
    }

    return <>
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
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        <button onClick={saveLanguage}>Save</button>
    </>
}
