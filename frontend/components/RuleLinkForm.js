import {useState} from "react";
import {addRuleLink} from "@/api";

export default function RuleLinkForm(props) {
    const [linkRuleName, setLinkRuleName] = useState("")

    function saveLink() {
        addRuleLink(props.fromEntityId, linkRuleName, '~')
            .then((r) => {
                if (r.status === 200) {
                    props.submitted(r.status)
                }
                else {
                    r.json().then(jr => props.submitted(r.status, jr))
                }
            })
    }

    return <p>
        <label>Link to rule name:</label>{' '}
        <input type="text" value={linkRuleName} onChange={(e) => setLinkRuleName(e.target.value)}/>{' '}
        <button onClick={() => saveLink()}>Save</button>
    </p>
}