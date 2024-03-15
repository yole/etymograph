import {useState} from "react";
import {addRuleLink} from "@/api";

export default function RuleLinkForm(props) {
    const [linkRuleName, setLinkRuleName] = useState("")
    const [source, setSource] = useState("")

    function saveLink() {
        addRuleLink(props.fromEntityId, linkRuleName, '~', source)
            .then((r) => {
                if (r.status === 200) {
                    props.submitted(r.status)
                }
                else {
                    r.json().then(jr => props.submitted(r.status, jr))
                }
            })
    }

    return <>
        <table><tbody>
        <tr>
            <td>Link to rule name:</td>
            <input type="text" value={linkRuleName} onChange={(e) => setLinkRuleName(e.target.value)}/>{' '}
        </tr>
        <tr>
            <td>Source:</td>
            <td><input type="text" value={source} onChange={e => setSource(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <button onClick={() => saveLink()}>Save</button>
    </>
}
