import {useRouter} from "next/router";
import PublicationForm from "@/components/PublicationForm";

export default function PublicationEditor() {
    const router = useRouter()

    function submitted(id) {
        router.push("/publication/" + id)
    }

    return <PublicationForm submitted={submitted}/>
}
